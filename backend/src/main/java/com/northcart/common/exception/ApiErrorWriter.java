package com.northcart.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northcart.common.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes an ApiError directly to the servlet response.
 *
 * <p>Needed because 401/403 raised by Spring Security FILTERS happen before the request
 * reaches a controller, so @RestControllerAdvice never sees them.</p>
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper; // Spring Boot's, so Instant is written as ISO-8601

    public void write(HttpServletResponse response, HttpStatus status, ErrorCode code,
                      String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(status, code, message, path));
    }
}
