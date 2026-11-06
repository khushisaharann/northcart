package com.northcart.user.entity;

/** Spring Security expects authorities like "ROLE_ADMIN"; the prefix is added in Phase 4. */
public enum RoleName {
    CUSTOMER,
    ADMIN
}
