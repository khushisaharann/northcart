package com.northcart.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's authorization checks.
 *
 * <pre>
 * Authorization: Bearer eyJhbGciOi...
 *   1. no header / not "Bearer"  -> continue as anonymous
 *   2. verify signature + expiry -> extract user id
 *   3. load user from DB         -> must still exist and be enabled
 *   4. put an Authentication into the SecurityContext -> request is "logged in"
 * </pre>
 *
 * <p>This filter never rejects a request itself. If the token is bad it just doesn't
 * authenticate, and later the authorization rules decide: public endpoints still work,
 * protected ones get a 401 from RestAuthenticationEntryPoint (which reads the reason we
 * store in a request attribute, e.g. "Token has expired").</p>
 *
 * <p>Deliberately NOT a @Component: Spring Boot registers every Filter bean with the
 * servlet container too, so it would run twice (once outside Spring Security).</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "northcart.jwtError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Long userId = jwtService.extractUserId(token);
            AuthenticatedUser user = userDetailsService.loadUserById(userId);

            if (!user.isEnabled()) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, "This account has been disabled");
            } else {
                user.eraseCredentials();
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "Invalid token");
        } catch (UsernameNotFoundException e) {
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "User no longer exists");
        }

        chain.doFilter(request, response);
    }
}
