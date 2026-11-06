package com.northcart.common.dto;

import com.northcart.common.exception.ErrorCode;
import com.northcart.common.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The single error format returned by every endpoint.
 *
 * <pre>
 * {
 *   "timestamp": "2026-11-06T10:15:30Z",
 *   "status": 409,
 *   "error": "Conflict",
 *   "code": "INSUFFICIENT_STOCK",
 *   "message": "Only 2 units of 'Wireless Mouse' are available",
 *   "path": "/api/v1/cart/items",
 *   "fieldErrors": [],
 *   "requestId": "4f9c2a1b"
 * }
 * </pre>
 *
 * The frontend shows {@code message} in a toast and maps {@code fieldErrors} onto form
 * inputs. {@code code} is stable and machine-readable; {@code message} is for humans.
 * {@code requestId} matches the X-Request-Id header and the server logs.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors,
        String requestId
) {
    public record FieldErrorDetail(String field, String message) {
    }

    public static ApiError of(HttpStatus status, ErrorCode code, String message, String path) {
        return of(status, code, message, path, List.of());
    }

    public static ApiError of(HttpStatus status, ErrorCode code, String message, String path,
                              List<FieldErrorDetail> fieldErrors) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, path, fieldErrors, MDC.get(RequestIdFilter.MDC_KEY));
    }
}
