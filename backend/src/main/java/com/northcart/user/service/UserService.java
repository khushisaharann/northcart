package com.northcart.user.service;

import com.northcart.common.exception.InvalidStateException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.user.dto.UpdateProfileRequest;
import com.northcart.user.dto.UserResponse;
import com.northcart.user.entity.RoleName;
import com.northcart.user.entity.User;
import com.northcart.user.mapper.UserMapper;
import com.northcart.user.repository.UserRepository;
import com.northcart.user.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user module's public API for other modules (address, cart, orders ...). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return UserMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        // No save(): 'user' is managed, dirty checking issues the UPDATE on commit.
        return UserMapper.toResponse(user);
    }

    /**
     * A reference (proxy) to the user WITHOUT a SELECT, for setting foreign keys,
     * e.g. address.setUser(...). Only safe when the id is known to exist, as it is for
     * the logged-in user.
     */
    @Transactional(readOnly = true)
    public User getReference(Long userId) {
        return userRepository.getReferenceById(userId);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<User> searchCustomers(String text, int page, int size) {
        return userRepository.findAll(UserSpecifications.customers(text),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    /**
     * Disabling takes effect on the customer's very next request: the JWT filter re-reads
     * the user and rejects disabled accounts, and login is refused.
     */
    @Transactional
    public User setCustomerEnabled(Long userId, boolean enabled) {
        User user = findUser(userId);
        if (user.hasRole(RoleName.ADMIN)) {
            throw new InvalidStateException("Admin accounts can't be disabled here");
        }
        user.setEnabled(enabled);
        return user;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
