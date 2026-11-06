package com.northcart.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who may call what.
 *
 * <p>Rules are evaluated top to bottom; the FIRST match wins, so specific rules go first
 * and {@code anyRequest()} is always last. Default is "deny" (authenticated required),
 * so a new endpoint is protected unless we explicitly open it.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize on service/controller methods if we need finer rules
public class SecurityConfig {

    private static final String[] API_DOCS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   CustomUserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF attacks abuse cookies the browser sends automatically. We authenticate
                // with an Authorization header the browser never adds on its own, so CSRF
                // protection is unnecessary for this stateless API.
                .csrf(AbstractHttpConfigurer::disable)
                // Uses the CorsConfigurationSource bean from CorsConfig.
                .cors(Customizer.withDefaults())
                // No HttpSession: every request must carry its own token.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // CORS preflight requests carry no token
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**").permitAll()
                        .requestMatchers(API_DOCS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // Role-based
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/payments/**")
                        .hasRole("CUSTOMER")
                        // Everything else (e.g. /api/v1/users/me): any logged-in user
                        .anyRequest().authenticated())

                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint) // 401 JSON
                        .accessDeniedHandler(accessDeniedHandler))          // 403 JSON

                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt: salted, deliberately slow hash. Strength 12 = 2^12 rounds, roughly 0.2-0.3 s
     * per hash: unnoticeable for one login, very expensive for an attacker guessing billions.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Spring Boot builds a DaoAuthenticationProvider from our UserDetailsService +
     * PasswordEncoder beans; this exposes the resulting AuthenticationManager for AuthService.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
