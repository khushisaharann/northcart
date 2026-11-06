package com.northcart.cart.service;

import com.northcart.cart.dto.AddCartItemRequest;
import com.northcart.cart.dto.CartItemIssue;
import com.northcart.cart.dto.CartItemResponse;
import com.northcart.cart.dto.CartResponse;
import com.northcart.cart.dto.UpdateCartItemRequest;
import com.northcart.cart.entity.Cart;
import com.northcart.cart.entity.CartItem;
import com.northcart.cart.repository.CartRepository;
import com.northcart.catalog.entity.Product;
import com.northcart.catalog.service.ProductService;
import com.northcart.common.exception.InsufficientStockException;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.inventory.entity.Inventory;
import com.northcart.inventory.service.InventoryService;
import com.northcart.pricing.Money;
import com.northcart.pricing.ShippingFeeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shopping cart, persisted in PostgreSQL (survives logout and works across devices).
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>One cart per user, created on the first "add", never on a plain GET.</li>
 *   <li>Stock is CHECKED on add/update but not RESERVED: reserving would let anyone block
 *       stock just by filling carts. Stock is reserved at checkout (Phase 7).</li>
 *   <li>Every write locks the cart row (SELECT ... FOR UPDATE), so parallel requests from
 *       the same user can't corrupt quantities.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CartService {

    public static final int MAX_QUANTITY_PER_ITEM = 10;
    public static final int MAX_DISTINCT_ITEMS = 50;

    private final CartRepository cartRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ShippingFeeCalculator shippingFeeCalculator;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findWithItemsByUserId(userId)
                .map(this::toResponse)
                .orElseGet(this::emptyCart);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        cartRepository.insertIfAbsent(userId);
        Cart cart = cartRepository.findByUserIdForUpdate(userId).orElseThrow();
        Product product = productService.getActiveProduct(request.productId()); // 404 if not buyable

        CartItem existing = cart.findItemByProductId(product.getId()).orElse(null);
        int newQuantity = (existing == null ? 0 : existing.getQuantity()) + request.quantity();
        checkQuantity(product, newQuantity);

        if (existing != null) {
            existing.setQuantity(newQuantity);
        } else {
            if (cart.getItems().size() >= MAX_DISTINCT_ITEMS) {
                throw new InvalidStateException("Your cart can hold at most " + MAX_DISTINCT_ITEMS + " different products");
            }
            cart.addItem(new CartItem(product, newQuantity));
        }
        return flushAndRespond(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = lockCart(userId, itemId);
        CartItem item = findOwnItem(cart, itemId);
        Product product = item.getProduct();
        if (!ProductService.isPurchasable(product)) {
            throw new InvalidStateException("'" + product.getName() + "' is no longer available. Please remove it.");
        }
        checkQuantity(product, request.quantity());
        item.setQuantity(request.quantity());
        return flushAndRespond(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = lockCart(userId, itemId);
        cart.removeItem(findOwnItem(cart, itemId)); // orphanRemoval deletes the row
        return flushAndRespond(cart);
    }

    // ------------------------------------------------------------------ used by checkout

    /**
     * Locks the cart for checkout. Holding this lock while the order is created also
     * serialises double-submitted checkouts of the same user.
     */
    @Transactional
    public Optional<Cart> lockCartForCheckout(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId);
    }

    /** Empties the cart after a successful order (orphanRemoval deletes the rows). */
    @Transactional
    public void clear(Cart cart) {
        cart.clear();
    }

    // ------------------------------------------------------------------

    /** Update/remove on a user without a cart: the item simply doesn't exist (404). */
    private Cart lockCart(Long userId, Long itemId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
    }

    /**
     * Writes pending changes now, so a newly added line has its database id in the
     * response. (New CartItems are only INSERTed at flush time via cascade.)
     */
    private CartResponse flushAndRespond(Cart cart) {
        cartRepository.flush();
        return toResponse(cart);
    }

    /**
     * Looking the item up INSIDE this user's cart is the ownership check: someone else's
     * itemId is just "not found", and we don't reveal that it exists.
     */
    private static CartItem findOwnItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(i -> Objects.equals(i.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId));
    }

    private void checkQuantity(Product product, int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new InvalidStateException("You can buy at most " + MAX_QUANTITY_PER_ITEM
                    + " units of '" + product.getName() + "'");
        }
        int available = inventoryService.getForProduct(product.getId()).getQuantityAvailable();
        if (quantity > available) {
            throw new InsufficientStockException(product.getName(), available);
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cart.getItems();
        Map<Long, Inventory> stock = inventoryService.findByProductIds(
                items.stream().map(i -> i.getProduct().getId()).toList());

        List<CartItemResponse> lines = new ArrayList<>();
        BigDecimal subtotal = Money.zero();
        int totalQuantity = 0;
        boolean allOk = !items.isEmpty();

        for (CartItem item : items) {
            Product p = item.getProduct();
            Inventory inv = stock.get(p.getId());
            int available = inv == null ? 0 : inv.getQuantityAvailable();
            CartItemIssue issue = issueFor(p, item.getQuantity(), available);
            BigDecimal lineTotal = Money.times(p.getPrice(), item.getQuantity());

            lines.add(new CartItemResponse(item.getId(), p.getId(), p.getName(), p.getSlug(),
                    p.getImages().isEmpty() ? null : p.getImages().get(0).getUrl(),
                    Money.of(p.getPrice()), item.getQuantity(), lineTotal, available, issue));

            subtotal = subtotal.add(lineTotal);
            totalQuantity += item.getQuantity();
            allOk &= issue == null;
        }

        BigDecimal shipping = shippingFeeCalculator.feeFor(subtotal);
        return new CartResponse(lines, totalQuantity, subtotal, shipping, subtotal.add(shipping), allOk);
    }

    private static CartItemIssue issueFor(Product product, int quantity, int available) {
        if (!ProductService.isPurchasable(product)) return CartItemIssue.UNAVAILABLE;
        if (available == 0) return CartItemIssue.OUT_OF_STOCK;
        if (available < quantity) return CartItemIssue.INSUFFICIENT_STOCK;
        return null;
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), 0, Money.zero(), Money.zero(), Money.zero(), false);
    }
}
