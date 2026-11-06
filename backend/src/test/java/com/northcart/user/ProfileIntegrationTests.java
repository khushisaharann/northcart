package com.northcart.user;

import com.northcart.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTests extends AbstractIntegrationTest {

    private static Map<String, Object> address(String line1, Boolean makeDefault) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Khushi Kumar");
        body.put("phone", "+91 98765 43210");
        body.put("line1", line1);
        body.put("city", "Noida");
        body.put("state", "Uttar Pradesh");
        body.put("postalCode", "201301");
        body.put("makeDefault", makeDefault);
        return body;
    }

    @Test
    void getProfile_returnsOnlyMyData() throws Exception {
        String email = uniqueEmail();
        String token = registerCustomer(email);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.addresses").isEmpty());
    }

    @Test
    void updateProfile_changesNameAndPhone() throws Exception {
        String token = registerCustomer(uniqueEmail());

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Khushi K", "phone", "+91 90000 00000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Khushi K"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.user.fullName").value("Khushi K"))
                .andExpect(jsonPath("$.user.phone").value("+91 90000 00000"));
    }

    @Test
    void updateProfile_cannotChangeEmail() throws Exception {
        String token = registerCustomer(uniqueEmail());
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "X", "email", "evil@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field 'email'"));
    }

    @Test
    void firstAddressBecomesDefault_andNewDefaultReplacesOld() throws Exception {
        String token = registerCustomer(uniqueEmail());

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("First street", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true))   // first one is forced default
                .andExpect(jsonPath("$.country").value("India"));

        // Exercises the partial unique index: old default must be cleared BEFORE the insert.
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("Second street", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[0].line1").value("Second street"))
                .andExpect(jsonPath("$.addresses[0].defaultAddress").value(true))
                .andExpect(jsonPath("$.addresses[1].defaultAddress").value(false));
    }

    @Test
    void addressesAreIsolatedBetweenUsers() throws Exception {
        String alice = registerCustomer(uniqueEmail());
        String bob = registerCustomer(uniqueEmail());

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("Alice street", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.addresses").isEmpty());
    }

    @Test
    void invalidAddress_returns400() throws Exception {
        String token = registerCustomer(uniqueEmail());
        Map<String, Object> body = address("", false);
        body.put("phone", "abc");

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'line1')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'phone')]").exists());
    }
}
