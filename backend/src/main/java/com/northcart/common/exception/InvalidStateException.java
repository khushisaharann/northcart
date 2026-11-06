package com.northcart.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 - the operation is not allowed in the resource's current state,
 * e.g. cancelling an order that has already SHIPPED.
 */
public class InvalidStateException extends BusinessException {

    public InvalidStateException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, message);
    }
}
