package com.northcart.cart;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartIntegrationTests extends AbstractIntegrationTest {

    private String customerEmail;
    private String customer;
    private String categoryId;

    @BeforeEach
    void setUp() throws Exception {
        customerEmail = uniqueEmail();
        customer = registerCustomer(customerEmail);
        categoryId = createCategory()[0];
    }

    // ------------------------------------------------------------------ helpers

    private long product(String name, String price, int stock) throws Exception {
        return id(createProduct(categoryId, name, price, stock));
    }

    private ResultActions add(String token, long productId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/cart/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("productId", productId, "quantity", quantity))));
    }

    private ResultActions getCart(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(token)));
    }

    private long firstItemId(String token) throws Exception {
        String body = getCart(token).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.items[0].itemId")).longValue();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void newCustomer_hasEmptyCart_andNoCartRowIsCreatedByReading() throws Exception {
        getCart(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value(0.0))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        Integer carts = jdbc.queryForObject(
                "SELECT count(*) FROM carts c JOIN users u ON u.id = c.user_id WHERE u.email = ?",
                Integer.class, customerEmail);
        assertThat(carts).isZero();
    }

    @Test
    void addingSameProductTwice_mergesLines_andTotalsAreCalculated() throws Exception {
        long mouse = product("Cart Mouse", "500.00", 10);
        long cable = product("Cart Cable", "300.00", 5);

        add(customer, mouse, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemId").isNumber());   // id present right away
        add(customer, mouse, 1).andExpect(status().isOk());
        add(customer, cable, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1500.0))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$.totalQuantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(1800.0))
                .andExpect(jsonPath("$.shippingFee").value(0.0))       // free above 999
                .andExpect(jsonPath("$.total").value(1800.0))
                .andExpect(jsonPath("$.checkoutReady").value(true))
                .andExpect(jsonPath("$.items[0].issue").value(nullValue()));
    }

    @Test
    void smallOrder_paysFlatShipping() throws Exception {
        long pen = product("Cart Pen", "300.00", 5);
        add(customer, pen, 1)
                .andExpect(jsonPath("$.subtotal").value(300.0))
                .andExpect(jsonPath("$.shippingFee").value(49.0))
                .andExpect(jsonPath("$.total").value(349.0));
    }

    @Test
    void cannotAddMoreThanInStock() throws Exception {
        long lamp = product("Cart Lamp", "900.00", 5);
        add(customer, lamp, 4).andExpect(status().isOk());

        add(customer, lamp, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Only 5 unit(s) of 'Cart Lamp' are available"));

        getCart(customer).andExpect(jsonPath("$.items[0].quantity").value(4)); // unchanged
    }

    @Test
    void outOfStockAndUnknownProducts_areRejected() throws Exception {
        long gone = product("Cart Sold Out", "100.00", 0);
        add(customer, gone, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("'Cart Sold Out' is out of stock"));

        add(customer, 999_999_999L, 1).andExpect(status().isNotFound());
    }

    @Test
    void quantityLimits() throws Exception {
        long book = product("Cart Book", "100.00", 50);

        add(customer, book, 0).andExpect(status().isBadRequest());
        add(customer, book, 11).andExpect(status().isBadRequest());

        add(customer, book, 8).andExpect(status().isOk());
        add(customer, book, 5)                                      // 8 + 5 = 13 > 10
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void updateQuantity_isStockChecked() throws Exception {
        long mug = product("Cart Mug", "250.00", 6);
        add(customer, mug, 1);
        long itemId = firstItemId(customer);

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(6));

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 7))))
                .andExpect(status().isConflict());
    }

    @Test
    void customersCannotTouchEachOthersCartItems() throws Exception {
        long hat = product("Cart Hat", "400.00", 5);
        add(customer, hat, 1);
        long itemId = firstItemId(customer);

        String other = registerCustomer(uniqueEmail());
        add(other, hat, 1); // other customer has a cart too

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", 2))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());

        getCart(customer).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void removeItem() throws Exception {
        long bag = product("Cart Bag", "1200.00", 3);
        add(customer, bag, 1);
        long itemId = firstItemId(customer);

        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0.0));

        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("Authorization", bearer(customer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartReportsProblemsWhenStockOrAvailabilityChangesLater() throws Exception {
        long chair = product("Cart Chair", "2000.00", 10);
        long desk = product("Cart Desk", "5000.00", 10);
        add(customer, chair, 3);
        add(customer, desk, 1);

        // Admin sells/removes stock so only 2 chairs remain
        mockMvc.perform(patch("/api/v1/admin/inventory/" + chair)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", -8))))
                .andExpect(status().isOk());

        // Admin deactivates the desk
        Map<String, Object> update = new HashMap<>();
        update.put("name", "Cart Desk");
        update.put("price", "5000.00");
        update.put("categoryId", Long.valueOf(categoryId));
        update.put("active", false);
        update.put("lowStockThreshold", 5);
        update.put("images", List.of());
        mockMvc.perform(put("/api/v1/admin/products/" + desk)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk());

        getCart(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issue").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.items[1].issue").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        // A deactivated product can no longer be added
        add(customer, desk, 1).andExpect(status().isNotFound());
    }

    @Test
    void cartIsPersisted_acrossLogins() throws Exception {
        String email = uniqueEmail();
        String first = registerCustomer(email);
        long plant = product("Cart Plant", "350.00", 4);
        add(first, plant, 2);

        String second = login(email, DEFAULT_PASSWORD);   // e.g. on another device
        getCart(second).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void parallelAddsFromSameUser_areSerialised() throws Exception {
        long sock = product("Cart Socks", "150.00", 10);
        int requests = 5;

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                tasks.add(() -> add(customer, sock, 1).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                statuses.add(f.get());
            }
            // Without SELECT ... FOR UPDATE, some requests would lose updates or hit the
            // uk_cart_items_cart_product constraint.
            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdown();
        }

        getCart(customer)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(requests));
    }
}
