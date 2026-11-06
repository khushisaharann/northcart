package com.northcart.common.exception;

import org.springframework.http.HttpStatus;

/** 404 - e.g. new ResourceNotFoundException("Product", "wireless-mouse"). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                resource + " not found: " + identifier);
    }
}
