package com.northcart.user.controller;

import com.northcart.address.service.AddressService;
import com.northcart.config.OpenApiConfig;
import com.northcart.security.AuthenticatedUser;
import com.northcart.user.dto.ProfileResponse;
import com.northcart.user.dto.UpdateProfileRequest;
import com.northcart.user.dto.UserResponse;
import com.northcart.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "/me" endpoints: the user id ALWAYS comes from the verified token, never from the URL
 * or body. There is no /users/{id} a customer could change to read someone else's data
 * (this class of bug is called IDOR - Insecure Direct Object Reference).
 */
@Tag(name = "Profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    @Operation(summary = "Get my profile and saved addresses")
    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedUser me) {
        return new ProfileResponse(userService.getUser(me.getId()), addressService.listForUser(me.getId()));
    }

    @Operation(summary = "Update my name and phone number")
    @PutMapping
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser me,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(me.getId(), request);
    }
}
