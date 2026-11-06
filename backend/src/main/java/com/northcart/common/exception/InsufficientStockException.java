package com.northcart.common.exception;

import org.springframework.http.HttpStatus;

/** 409 - the requested quantity exceeds what is in stock. */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String productName, int available) {
        super(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK,
                available == 0
                        ? "'" + productName + "' is out of stock"
                        : "Only " + available + " unit(s) of '" + productName + "' are available");
    }
}
