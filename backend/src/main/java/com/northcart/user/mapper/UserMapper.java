package com.northcart.user.mapper;

import com.northcart.user.dto.UserResponse;
import com.northcart.user.entity.User;

import java.util.stream.Collectors;

/** Entity -> DTO. Hand-written so every mapped field is visible and intentional. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet()),
                user.getCreatedAt());
    }
}
