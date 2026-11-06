package com.northcart.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for every expected, business-level failure ("product not found",
 * "not enough stock"). Each subclass decides its HTTP status and error code, so the
 * global handler needs just one method for all of them.
 *
 * <p>Unchecked (extends RuntimeException) so it rolls back @Transactional methods by
 * default and doesn't force throws clauses through every layer.</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public BusinessException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
