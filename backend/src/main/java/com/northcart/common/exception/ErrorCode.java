package com.northcart.common.exception;

/**
 * Machine-readable error codes. The frontend may branch on these
 * (e.g. INSUFFICIENT_STOCK -> refresh the cart), so never rename one once released.
 */
public enum ErrorCode {
    // 400
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    PAYMENT_VERIFICATION_FAILED,
    INVALID_TOKEN,          // password-reset link invalid / expired / used
    // 401 / 403
    UNAUTHORIZED,
    INVALID_CREDENTIALS,
    FORBIDDEN,
    ACCOUNT_DISABLED,
    // 404 / 405
    RESOURCE_NOT_FOUND,
    ENDPOINT_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    // 409
    DUPLICATE_RESOURCE,
    INSUFFICIENT_STOCK,
    INVALID_STATE,
    PAYMENT_LATE,               // paid after the order was cancelled; refund issued
    CONCURRENT_MODIFICATION,
    DATA_INTEGRITY_VIOLATION,
    // 500 / 502
    INTERNAL_ERROR,
    PAYMENT_GATEWAY_ERROR
}
