package com.northcart.order.service;

import com.northcart.address.entity.Address;
import com.northcart.address.service.AddressService;
import com.northcart.cart.entity.Cart;
import com.northcart.cart.entity.CartItem;
import com.northcart.cart.service.CartService;
import com.northcart.catalog.entity.Product;
import com.northcart.catalog.service.ProductService;
import com.northcart.common.dto.PageParams;
import com.northcart.common.dto.PageResponse;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.config.AppProperties;
import com.northcart.inventory.service.InventoryService;
import com.northcart.order.dto.CheckoutRequest;
import com.northcart.order.dto.AdminOrderSummary;
import com.northcart.order.dto.AdminOrderView;
import com.northcart.order.dto.OrderEmailView;
import com.northcart.order.dto.OrderResponse;
import com.northcart.order.dto.OrderSummaryResponse;
import com.northcart.order.entity.Order;
import com.northcart.order.entity.OrderItem;
import com.northcart.order.entity.OrderStatus;
import com.northcart.order.entity.ShippingAddress;
import com.northcart.order.event.OrderPlacedEvent;
import com.northcart.order.event.OrderStatusChangedEvent;
import com.northcart.order.mapper.OrderMapper;
import com.northcart.order.repository.OrderRepository;
import com.northcart.order.repository.OrderSpecifications;
import com.northcart.pricing.ShippingFeeCalculator;
import com.northcart.user.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Checkout and the order lifecycle.
 *
 * <p>Checkout is ONE database transaction: either the order exists, the stock is
 * reserved and the cart is empty, or none of it happened. There is no state in between
 * for a crash or an error to leave behind.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    static final int EXPIRY_BATCH_SIZE = 100;
    private static final java.time.format.DateTimeFormatter EMAIL_TIME = java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm 'IST'", java.util.Locale.ENGLISH)
            .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final AddressService addressService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final UserService userService;
    private final ShippingFeeCalculator shippingFeeCalculator;
    private final OrderNumberGenerator orderNumberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /** Result of a checkout: created=false means an idempotent replay of an earlier submit. */
    public record CheckoutResult(OrderResponse order, boolean created) {
    }

    // ------------------------------------------------------------------ checkout

    @Transactional
    public CheckoutResult placeOrder(Long userId, CheckoutRequest request, String idempotencyKey) {
        // 1. Lock the cart FIRST. A second submit of the same checkout waits here until the
        //    first one commits, and then finds the finished order in step 2.
        Cart cart = cartService.lockCartForCheckout(userId).orElse(null);

        // 2. Idempotency: same key = same order, returned again instead of a duplicate.
        if (idempotencyKey != null) {
            var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay of order {}", existing.get().getOrderNumber());
                return new CheckoutResult(OrderMapper.toResponse(existing.get()), false);
            }
        }

        if (cart == null || cart.getItems().isEmpty()) {
            throw new InvalidStateException("Your cart is empty");
        }

        // 3. Address must belong to this user (404 otherwise); copied, not referenced.
        Address address = addressService.getOwnedAddress(userId, request.addressId());

        Order order = new Order();
        order.setOrderNumber(orderNumberGenerator.next());
        order.setUser(userService.getReference(userId));
        order.setShippingAddress(ShippingAddress.snapshotOf(address));
        order.setIdempotencyKey(idempotencyKey);

        // 4. Re-validate every line and reserve stock. Sorted by product id: two checkouts
        //    locking the same products in the same order can't deadlock each other.
        List<CartItem> lines = cart.getItems().stream()
                .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                .toList();
        for (CartItem line : lines) {
            Product product = line.getProduct();
            if (!ProductService.isPurchasable(product)) {
                throw new InvalidStateException("'" + product.getName()
                        + "' is no longer available. Please remove it from your cart.");
            }
            inventoryService.reserve(product.getId(), product.getName(), line.getQuantity()); // 409 if short
            productService.recordSale(product.getId(), line.getQuantity());
            order.addItem(OrderItem.snapshotOf(product, line.getQuantity())); // price frozen here
        }

        // 5. Totals: computed by the server from snapshots, same rule as the cart.
        order.recalculateTotals();
        order.setShippingFee(shippingFeeCalculator.feeFor(order.getSubtotal()));
        order.recalculateTotals();

        orderRepository.save(order);
        cartService.clear(cart);

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), order.getOrderNumber()));
        log.info("Order {} placed: userId={}, total={}", order.getOrderNumber(), userId, order.getTotal());
        return new CheckoutResult(OrderMapper.toResponse(order), true);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listForUser(Long userId, PageParams params) {
        var page = orderRepository.findByUserId(userId, PageRequest.of(
                params.pageOrDefault(), params.sizeOrDefault(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.from(page, OrderMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderResponse getForUser(Long userId, String orderNumber) {
        return orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
                .map(OrderMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
    }

    // ------------------------------------------------------------------ cancellation

    @Transactional
    public OrderResponse cancelByCustomer(Long userId, String orderNumber) {
        Order order = orderRepository.findForUpdate(orderNumber, userId) // row lock: no double cancel
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        if (!order.getStatus().isCancellable()) {
            throw new InvalidStateException("Order " + orderNumber + " can no longer be cancelled (status: "
                    + order.getStatus() + ")");
        }
        cancel(order);
        log.info("Order {} cancelled by customer", orderNumber);
        return OrderMapper.toResponse(order);
    }

    /**
     * Cancels PLACED orders that weren't paid within app.orders.payment-timeout and puts
     * their stock back. Called every minute by OrderExpiryScheduler.
     *
     * @return number of orders cancelled
     */
    @Transactional
    public int expireUnpaidOrders() {
        return expireUnpaidOrders(Set.of());
    }

    /**
     * @param excludedOrderIds orders whose payment status couldn't be checked with the
     *                         gateway right now; they are left alone until the next run
     */
    @Transactional
    public int expireUnpaidOrders(Collection<Long> excludedOrderIds) {
        Instant cutoff = clock.instant().minus(properties.orders().paymentTimeout());
        List<Long> ids = orderRepository.lockExpiredPlacedOrderIds(cutoff, EXPIRY_BATCH_SIZE).stream()
                .filter(id -> !excludedOrderIds.contains(id))
                .toList();
        for (Order order : orderRepository.findAllById(ids)) {
            cancel(order);
            log.info("Order {} expired: not paid within {}", order.getOrderNumber(),
                    properties.orders().paymentTimeout());
        }
        return ids.size();
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummary> searchForAdmin(OrderStatus status, String text, PageParams params) {
        var page = orderRepository.findAll(OrderSpecifications.adminSearch(status, text), PageRequest.of(
                params.pageOrDefault(), params.sizeOrDefault(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.from(page, o -> new AdminOrderSummary(o.getOrderNumber(), o.getUser().getFullName(),
                o.getUser().getEmail(), o.getStatus(), o.getTotalQuantity(), o.getTotal(), o.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminOrderView getForAdmin(String orderNumber) {
        return toAdminView(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
    }

    /**
     * Admin moves an order along: CONFIRMED -> SHIPPED -> DELIVERED, or cancels it.
     * Same row lock and the same state machine as every other status change, and the same
     * events (so the customer gets a status email and paid cancellations are refunded).
     */
    @Transactional
    public AdminOrderView changeStatusByAdmin(String orderNumber, OrderStatus target) {
        Order found = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        Order order = lockOrderById(found.getId());
        OrderStatus previous = order.getStatus();

        if (!previous.adminTransitions().contains(target)) {
            throw new InvalidStateException("Order " + orderNumber + " can't be moved from " + previous + " to "
                    + target + (target == OrderStatus.CONFIRMED ? " (orders are confirmed by payment)" : ""));
        }
        if (target == OrderStatus.CANCELLED) {
            cancel(order); // releases stock; payment module refunds if it was paid
        } else {
            order.changeStatus(target, clock.instant());
            eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getId(), orderNumber, previous, target));
        }
        log.info("Admin moved order {} from {} to {}", orderNumber, previous, target);
        return toAdminView(order);
    }

    private AdminOrderView toAdminView(Order o) {
        var u = o.getUser();
        return new AdminOrderView(o.getId(), OrderMapper.toResponse(o),
                new AdminOrderView.Customer(u.getId(), u.getFullName(), u.getEmail(), u.getPhone()),
                o.getStatus().adminTransitions());
    }

    // ------------------------------------------------------------------ used by the payment module

    /** Locks the customer's order row for a payment operation (404 if it isn't theirs). */
    @Transactional
    public Order lockOrderForPayment(Long userId, String orderNumber) {
        return orderRepository.findForUpdate(orderNumber, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
    }

    /**
     * Locks the order row AND re-reads it. Important when the entity may already be in the
     * persistence context: a plain "SELECT ... FOR UPDATE" would take the lock but keep the
     * old in-memory state, so we could act on a status that changed while we waited.
     * refresh() re-loads the row under the lock (and cascades to the items).
     */
    @Transactional
    public Order lockOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    /** PLACED -> CONFIRMED once the payment is verified. Caller holds the row lock. */
    @Transactional
    public void confirmPaid(Order order) {
        order.changeStatus(OrderStatus.CONFIRMED, clock.instant());
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getId(), order.getOrderNumber(), OrderStatus.PLACED, OrderStatus.CONFIRMED));
        log.info("Order {} confirmed (paid)", order.getOrderNumber());
    }

    @Transactional(readOnly = true)
    public OrderResponse toResponse(Order order) {
        return OrderMapper.toResponse(order);
    }

    /** Everything an email about this order needs, loaded in one read-only transaction. */
    @Transactional(readOnly = true)
    public OrderEmailView getEmailView(Long orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        var user = o.getUser();
        var a = o.getShippingAddress();
        String address = String.join(", ", java.util.stream.Stream.of(a.getFullName(), a.getLine1(), a.getLine2(),
                a.getCity(), a.getState() + " " + a.getPostalCode(), a.getCountry())
                .filter(part -> part != null && !part.isBlank()).toList());
        return new OrderEmailView(user.getEmail(), user.getFullName(), o.getOrderNumber(), o.getStatus(),
                o.getItems().stream()
                        .map(i -> new OrderEmailView.Line(i.getProductName(), i.getQuantity(), i.getLineTotal()))
                        .toList(),
                o.getSubtotal(), o.getShippingFee(), o.getTotal(), address,
                EMAIL_TIME.format(o.getCreatedAt().plus(properties.orders().paymentTimeout())),
                properties.frontendUrl() + "/orders/" + o.getOrderNumber());
    }

    /** Shared by customer cancel, expiry and (Phase 10) admin cancel. Caller holds the row lock. */
    void cancel(Order order) {
        OrderStatus previous = order.getStatus();
        order.changeStatus(OrderStatus.CANCELLED, clock.instant());
        for (OrderItem item : order.getItems()) {
            inventoryService.release(item.getProduct().getId(), item.getQuantity());
            productService.recordSale(item.getProduct().getId(), -item.getQuantity());
        }
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getId(), order.getOrderNumber(), previous, OrderStatus.CANCELLED));
    }
}
