package com.northcart.user.dto;

import com.northcart.address.dto.AddressResponse;

import java.util.List;

/** GET /api/v1/users/me: the user plus their saved addresses (default first). */
public record ProfileResponse(
        UserResponse user,
        List<AddressResponse> addresses
) {
}
