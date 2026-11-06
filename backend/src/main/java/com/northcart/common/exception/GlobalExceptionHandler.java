package com.northcart.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.northcart.common.dto.ApiError;
import com.northcart.common.dto.ApiError.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts every exception into the ApiError JSON format.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Controllers and services never build error responses themselves; they throw.</li>
 *   <li>Expected errors (4xx) are logged at DEBUG/INFO; unexpected ones (5xx) at ERROR with
 *       the stack trace - but the client only ever sees a generic message, never internals.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Friendly messages for database constraints that a client can realistically trigger.
     * Keys are the constraint names from V1__init_schema.sql (Map.ofEntries: no 10-entry limit).
     */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.ofEntries(
            Map.entry("uk_users_email", "An account with this email already exists"),
            Map.entry("uk_products_sku", "A product with this SKU already exists"),
            Map.entry("uk_products_slug", "A product with this slug already exists"),
            Map.entry("uk_categories_name", "A category with this name already exists"),
            Map.entry("uk_categories_slug", "A category with this slug already exists"),
            Map.entry("uk_cart_items_cart_product", "This product is already in your cart"),
            Map.entry("fk_products_category", "This category still has products"),
            Map.entry("uk_orders_user_idempotency_key", "This order has already been submitted"),
            Map.entry("ck_inventory_quantity_non_negative", "Not enough stock for this product"));

    // ---------- Our own business exceptions (404, 409 ...) ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.debug("Business exception on {}: {}", req.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req);
    }

    // ---------- 401 / 403 thrown inside controllers/services ----------
    // (401/403 raised by the security FILTERS are written by RestAuthenticationEntryPoint
    //  and RestAccessDeniedHandler instead, because they never reach a controller.)

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        // Same message for "unknown email" and "wrong password": don't reveal which accounts exist.
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password", req);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_DISABLED, "This account has been disabled", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication failed", req);
    }

    /** Thrown by @PreAuthorize checks, which run inside the controller call. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action", req);
    }

    // ---------- 400: @Valid failed on a JSON body or on bound query parameters ----------

    /**
     * BindException covers both cases: MethodArgumentNotValidException (a JSON @RequestBody)
     * is a subclass, and query-parameter objects such as ProductSearchParams raise it too.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleValidation(BindException ex, HttpServletRequest req) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.add(new FieldErrorDetail(
                fe.getField(),
                // e.g. ?minPrice=abc: show a clean message instead of a ConversionFailedException text
                fe.isBindingFailure() ? "Invalid value" : fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.add(new FieldErrorDetail(ge.getObjectName(), ge.getDefaultMessage())));
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Request validation failed", req.getRequestURI(),
                fieldErrors));
    }

    // ---------- 400: @Validated path variables / request params ----------

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest req) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Request validation failed", req.getRequestURI(),
                fieldErrors));
    }

    // ---------- 400: unreadable JSON, unknown field, wrong type ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        String message = "Malformed JSON request body";
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException upe) {
            message = "Unknown field '" + upe.getPropertyName() + "'";
        } else if (cause instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            message = "Invalid value for field '" + ife.getPath().get(ife.getPath().size() - 1).getFieldName() + "'";
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, message, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> handleMissingInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, ex.getMessage(), req);
    }

    // ---------- 404 / 405: unknown URL or wrong HTTP method ----------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND, "Endpoint not found", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this endpoint", req);
    }

    // ---------- 409: database said no ----------

    /**
     * Safety net for races the service layer can't fully prevent, e.g. two requests
     * registering the same email at the same millisecond: both pass the "exists?" check,
     * but the UNIQUE constraint lets only one INSERT succeed.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        String constraint = extractConstraintName(ex);
        String message = constraint == null ? null : CONSTRAINT_MESSAGES.get(constraint);
        if (message == null) {
            // Unknown constraint = probably a bug in our code; log it fully, tell the client little.
            log.warn("Unmapped data integrity violation on {} (constraint={})",
                    req.getRequestURI(), constraint, ex);
            message = "The request conflicts with existing data";
        }
        ErrorCode code = constraint != null && constraint.startsWith("uk_")
                ? ErrorCode.DUPLICATE_RESOURCE
                : ErrorCode.DATA_INTEGRITY_VIOLATION;
        return build(HttpStatus.CONFLICT, code, message, req);
    }

    /** Two transactions updated the same @Version row; the second one loses. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "This item was modified by another request. Please refresh and try again.", req);
    }

    // ---------- 500: anything we didn't anticipate ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Other Spring MVC exceptions (415 unsupported media type, 406 not acceptable ...)
        // already carry the correct HTTP status; keep it instead of turning them into a 500.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, ErrorCode.MALFORMED_REQUEST, status.getReasonPhrase(), req);
        }
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong. Please try again later.", req);
    }

    // ---------- helpers ----------

    private static ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                                  HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, req.getRequestURI()));
    }

    /** Walks the cause chain to Hibernate's ConstraintViolationException, which knows the name. */
    private static String extractConstraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName().toLowerCase();
            }
        }
        return null;
    }

    /** "addItem.quantity" -> "quantity" */
    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
