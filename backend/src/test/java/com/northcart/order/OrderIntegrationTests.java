package com.northcart.order;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import com.northcart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    private String customer;
    private long addressId;
    private String categoryId;

    @BeforeEach
    void setUp() throws Exception {
        customer = registerCustomer(uniqueEmail());
        addressId = createAddress(customer);
        categoryId = createCategory()[0];
    }

    // ------------------------------------------------------------------ helpers

    private long product(String name, String price, int stock) throws Exception {
        return id(createProduct(categoryId, name, price, stock));
    }

    private ResultActions checkout(String token, long address, String key) throws Exception {
        var request = post("/api/v1/orders")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("addressId", address)));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private String placeOrder(long productId, int quantity) throws Exception {
        addToCart(customer, productId, quantity);
        String body = checkout(customer, addressId, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderNumber");
    }

    private int stockOf(long productId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.availableQuantity");
    }

    private int soldCountOf(long productId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.soldCount");
    }

    // ------------------------------------------------------------------ checkout

    @Test
    void checkout_createsOrder_reservesStock_andEmptiesCart() throws Exception {
        long keyboard = product("Order Keyboard", "3499.00", 10);
        long mouse = product("Order Mouse", "799.00", 20);
        addToCart(customer, keyboard, 1);
        addToCart(customer, mouse, 2);

        checkout(customer, addressId, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(matchesPattern("^NC-\\d{8}-[A-Z2-9]{6}$")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(5097.0))
                .andExpect(jsonPath("$.shippingFee").value(0.0))
                .andExpect(jsonPath("$.total").value(5097.0))
                .andExpect(jsonPath("$.shippingAddress.city").value("Noida"))
                .andExpect(jsonPath("$.placedAt").value(notNullValue()))
                .andExpect(jsonPath("$.cancellable").value(true));

        assertThat(stockOf(keyboard)).isEqualTo(9);
        assertThat(stockOf(mouse)).isEqualTo(18);
        assertThat(soldCountOf(mouse)).isEqualTo(2);

        mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void smallOrder_includesShippingFee() throws Exception {
        long pen = product("Order Pen", "120.00", 10);
        addToCart(customer, pen, 2);
        checkout(customer, addressId, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(240.0))
                .andExpect(jsonPath("$.shippingFee").value(49.0))
                .andExpect(jsonPath("$.total").value(289.0));
    }

    @Test
    void orderKeepsPriceAndNameSnapshot_whenProductChangesLater() throws Exception {
        long lamp = product("Order Lamp", "1500.00", 5);
        String orderNumber = placeOrder(lamp, 1);

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Order Lamp v2");
        update.put("price", "2500.00");
        update.put("categoryId", Long.valueOf(categoryId));
        update.put("active", true);
        update.put("lowStockThreshold", 5);
        update.put("images", List.of());
        mockMvc.perform(put("/api/v1/admin/products/" + lamp)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/" + orderNumber).header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.items[0].productName").value("Order Lamp"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(1500.0))
                .andExpect(jsonPath("$.total").value(1500.0));
    }

    @Test
    void sameIdempotencyKey_returnsSameOrder_andReservesStockOnce() throws Exception {
        long book = product("Order Book", "999.00", 10);
        addToCart(customer, book, 2);
        String key = UUID.randomUUID().toString();

        String first = checkout(customer, addressId, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = checkout(customer, addressId, key).andExpect(status().isOk())   // 200, not 201
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.orderNumber"))
                .isEqualTo(JsonPath.read(first, "$.orderNumber"));
        assertThat(stockOf(book)).isEqualTo(8);   // reserved once, not twice
    }

    @Test
    void emptyCart_cannotBeCheckedOut() throws Exception {
        checkout(customer, addressId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("Your cart is empty"));
    }

    @Test
    void insufficientStockAtCheckout_rollsBackEverything() throws Exception {
        long cheap = product("Order Cheap", "100.00", 10);
        long scarce = product("Order Scarce", "200.00", 5);
        addToCart(customer, cheap, 2);
        addToCart(customer, scarce, 3);

        // Stock drops after the items were added to the cart
        mockMvc.perform(patch("/api/v1/admin/inventory/" + scarce)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", -4))))
                .andExpect(status().isOk());

        checkout(customer, addressId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Only 1 unit(s) of 'Order Scarce' are available"));

        // All-or-nothing: the other product's reservation was rolled back, cart untouched
        assertThat(stockOf(cheap)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void cannotUseAnotherCustomersAddress() throws Exception {
        String other = registerCustomer(uniqueEmail());
        long othersAddress = createAddress(other);
        addToCart(customer, product("Order Cap", "300.00", 5), 1);

        checkout(customer, othersAddress, null).andExpect(status().isNotFound());
    }

    @Test
    void invalidIdempotencyKey_returns400() throws Exception {
        addToCart(customer, product("Order Sock", "99.00", 5), 1);
        checkout(customer, addressId, "x".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void twoCustomersRacingForTheLastUnits_neverOversell() throws Exception {
        long last = product("Order Last Units", "500.00", 3);
        String a = registerCustomer(uniqueEmail());
        String b = registerCustomer(uniqueEmail());
        long addressA = createAddress(a);
        long addressB = createAddress(b);
        addToCart(a, last, 3);   // carts only CHECK stock, so both can hold 3
        addToCart(b, last, 3);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> checkout(a, addressA, null).andReturn().getResponse().getStatus(),
                    () -> checkout(b, addressB, null).andReturn().getResponse().getStatus());
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(stockOf(last)).isZero();
    }

    // ------------------------------------------------------------------ history & details

    @Test
    void orderHistory_isNewestFirst_andPaginated() throws Exception {
        long item = product("Order Tea", "250.00", 20);
        String first = placeOrder(item, 1);
        String second = placeOrder(item, 2);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].orderNumber").value(contains(second, first)))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].itemPreview[0]").value("Order Tea"));

        mockMvc.perform(get("/api/v1/orders?size=1&page=1").header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.content[0].orderNumber").value(first));
    }

    @Test
    void customersCannotSeeOrCancelOthersOrders() throws Exception {
        String orderNumber = placeOrder(product("Order Private", "300.00", 5), 1);
        String other = registerCustomer(uniqueEmail());

        mockMvc.perform(get("/api/v1/orders/" + orderNumber).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/orders/" + orderNumber + "/cancel").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ cancellation & expiry

    @Test
    void cancel_returnsStock_andCanOnlyHappenOnce() throws Exception {
        long vase = product("Order Vase", "700.00", 4);
        String orderNumber = placeOrder(vase, 3);
        assertThat(stockOf(vase)).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/orders/" + orderNumber + "/cancel").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value(notNullValue()))
                .andExpect(jsonPath("$.cancellable").value(false));

        assertThat(stockOf(vase)).isEqualTo(4);
        assertThat(soldCountOf(vase)).isZero();

        mockMvc.perform(patch("/api/v1/orders/" + orderNumber + "/cancel").header("Authorization", bearer(customer)))
                .andExpect(status().isConflict());
        assertThat(stockOf(vase)).isEqualTo(4);   // not released twice
    }

    @Test
    void unpaidOrdersExpireAfterTimeout_andReleaseStock() throws Exception {
        long clock = product("Order Clock", "1200.00", 5);
        String oldOrder = placeOrder(clock, 2);
        String freshOrder = placeOrder(clock, 1);
        assertThat(stockOf(clock)).isEqualTo(2);

        // Pretend the first order was placed 31 minutes ago (timeout is 30m)
        jdbc.update("UPDATE orders SET created_at = now() - interval '31 minutes' WHERE order_number = ?", oldOrder);

        orderService.expireUnpaidOrders();

        mockMvc.perform(get("/api/v1/orders/" + oldOrder).header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/orders/" + freshOrder).header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.cancelledAt").value(nullValue()));
        assertThat(stockOf(clock)).isEqualTo(4);
    }
}
