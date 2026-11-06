package com.northcart.catalog;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog end-to-end. Every test creates its OWN category and filters by it, so tests
 * stay independent even though they share one database. Data helpers live in
 * AbstractIntegrationTest.
 */
class CatalogIntegrationTests extends AbstractIntegrationTest {

    // ------------------------------------------------------------------ create + read

    @Test
    void adminCreatesProduct_andCustomerCanSeeIt() throws Exception {
        String[] category = createCategory();
        Map<String, Object> body = productBody(category[0], "Wireless Mouse", "799.00", 25);
        body.put("sku", "t-mouse-" + unique());            // lower case on purpose
        String created = createProduct(body);
        String slug = JsonPath.read(created, "$.slug");

        mockMvc.perform(get("/api/v1/products/" + slug))    // anonymous: no token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"))
                .andExpect(jsonPath("$.sku").value(((String) body.get("sku")).toUpperCase()))
                .andExpect(jsonPath("$.price").value(799.00))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(25))
                .andExpect(jsonPath("$.category.slug").value(category[1]))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].altText").value("front"))
                .andExpect(jsonPath("$.images[1].sortOrder").value(1));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == '" + category[1] + "')].productCount").value(hasItem(1)));
    }

    @Test
    void sameNameGetsDistinctSlugs() throws Exception {
        String[] category = createCategory();
        String name = "Desk Lamp " + unique();
        String first = JsonPath.read(createProduct(category[0], name, "999.00", 1), "$.slug");
        String second = JsonPath.read(createProduct(category[0], name, "999.00", 1), "$.slug");
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first + "-2");
    }

    // ------------------------------------------------------------------ search

    @Test
    void searchFiltersSortsAndPaginates() throws Exception {
        String[] category = createCategory();
        createProduct(category[0], "Alpha Keyboard", "3000.00", 5);
        createProduct(category[0], "Beta Mouse", "800.00", 0);
        createProduct(category[0], "Gamma Keyboard Pro", "5000.00", 10);
        createProduct(category[0], "Delta Cable", "200.00", 3);
        String base = "/api/v1/products?category=" + category[1];

        // Category filter + price sort
        mockMvc.perform(get(base + "&sort=price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].name").value(
                        contains("Delta Cable", "Beta Mouse", "Alpha Keyboard", "Gamma Keyboard Pro")));

        // Prefix full-text search: "keyb" matches "Keyboard"
        mockMvc.perform(get(base + "&q=keyb"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Price range
        mockMvc.perform(get(base + "&minPrice=500&maxPrice=3500&sort=price_asc"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Beta Mouse", "Alpha Keyboard")));

        // In stock only: Beta Mouse (0 units) disappears
        mockMvc.perform(get(base + "&inStock=true"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.name == 'Beta Mouse')]").isEmpty());

        // Pagination: second page of size 1
        mockMvc.perform(get(base + "&sort=price_asc&size=1&page=1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Beta Mouse"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.first").value(false));

        // Out-of-stock products are still listed (unless inStock=true), flagged as such
        mockMvc.perform(get(base + "&q=beta"))
                .andExpect(jsonPath("$.content[0].inStock").value(false))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").isNotEmpty());
    }

    @Test
    void invalidSearchParams_return400() throws Exception {
        mockMvc.perform(get("/api/v1/products?size=500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'size')]").exists());

        mockMvc.perform(get("/api/v1/products?sort=cheapest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'sort')]").exists());

        mockMvc.perform(get("/api/v1/products?minPrice=100&maxPrice=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'priceRangeValid')]").exists());

        mockMvc.perform(get("/api/v1/products?minPrice=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'minPrice')].message").value(hasItem("Invalid value")));
    }

    // ------------------------------------------------------------------ update / soft delete

    @Test
    void deactivatedProductDisappearsForCustomersButNotForAdmins() throws Exception {
        String[] category = createCategory();
        String created = createProduct(category[0], "Old Phone Case", "299.00", 10);
        long productId = id(created);
        String slug = JsonPath.read(created, "$.slug");
        String sku = JsonPath.read(created, "$.sku");

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Old Phone Case");
        update.put("price", "299.00");
        update.put("categoryId", Long.valueOf(category[0]));
        update.put("active", false);
        update.put("lowStockThreshold", 5);
        update.put("images", List.of());
        mockMvc.perform(put("/api/v1/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.images").isEmpty());

        mockMvc.perform(get("/api/v1/products/" + slug)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products?category=" + category[1]))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/admin/products?q=" + sku).header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].active").value(false));
    }

    @Test
    void updateKeepsSlugAndReplacesImages() throws Exception {
        String[] category = createCategory();
        String created = createProduct(category[0], "Coffee Grinder", "2499.00", 10);
        long productId = id(created);
        String slug = JsonPath.read(created, "$.slug");

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Coffee Grinder Deluxe");
        update.put("description", "Now with 40 grind settings");
        update.put("price", "2799.00");
        update.put("categoryId", Long.valueOf(category[0]));
        update.put("active", true);
        update.put("lowStockThreshold", 3);
        update.put("images", List.of(Map.of("url", "https://img.example.com/new.jpg")));

        mockMvc.perform(put("/api/v1/admin/products/" + productId)
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.price").value(2799.00))
                .andExpect(jsonPath("$.lowStockThreshold").value(3));

        mockMvc.perform(get("/api/v1/products/" + slug))
                .andExpect(jsonPath("$.name").value("Coffee Grinder Deluxe"))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].url").value("https://img.example.com/new.jpg"));
    }

    @Test
    void duplicateSkuAndCategoryName_return409() throws Exception {
        String[] category = createCategory();
        Map<String, Object> body = productBody(category[0], "Mug", "199.00", 1);
        createProduct(body);
        body.put("name", "Another Mug");

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));

        String name = "Garden " + unique();
        mockMvc.perform(post("/api/v1/admin/categories").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/categories").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name.toUpperCase()))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidProduct_return400() throws Exception {
        String[] category = createCategory();
        Map<String, Object> body = productBody(category[0], "", "-5", -1);
        body.put("images", new ArrayList<>(List.of(Map.of("url", "http://insecure.example.com/a.jpg"))));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'price')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'initialStock')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'images[0].url')]").exists());
    }

    // ------------------------------------------------------------------ inventory

    @Test
    void stockAdjustmentsAreRelativeAndNeverNegative() throws Exception {
        String[] category = createCategory();
        long productId = id(createProduct(category[0], "Notebook", "99.00", 10));
        String url = "/api/v1/admin/inventory/" + productId;

        mockMvc.perform(patch(url).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", 15, "reason", "Restock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(25));

        mockMvc.perform(patch(url).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", -100))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Stock cannot go below zero. Available: 25"));

        mockMvc.perform(patch(url).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", 0))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/admin/inventory/999999999").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("delta", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void lowStockFilterShowsOnlyProductsAtOrBelowThreshold() throws Exception {
        String[] category = createCategory();
        createProduct(category[0], "Almost Gone", "100.00", 2);   // threshold defaults to 5
        createProduct(category[0], "Plenty Left", "100.00", 50);

        mockMvc.perform(get("/api/v1/admin/products?lowStock=true&category=" + category[1])
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Almost Gone"))
                .andExpect(jsonPath("$.content[0].lowStock").value(true));
    }

    // ------------------------------------------------------------------ security

    @Test
    void catalogWritesRequireAdmin() throws Exception {
        String customer = registerCustomer(uniqueEmail());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hacked"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hacked"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }
}
