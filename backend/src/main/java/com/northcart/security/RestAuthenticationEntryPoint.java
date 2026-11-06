package com.northcart.security;

import com.northcart.common.exception.ApiErrorWriter;
import com.northcart.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401: called when an ANONYMOUS request hits a protected endpoint
 * (no token, or a token the JWT filter rejected).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);
        String message = reason != null ? reason.toString() : "Authentication is required";
        apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                message, request.getRequestURI());
    }
}
