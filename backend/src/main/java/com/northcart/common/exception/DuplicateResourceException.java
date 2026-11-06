package com.northcart.common.exception;

import org.springframework.http.HttpStatus;

/** 409 - e.g. registering an email that already exists. */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
