package com.northcart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Services ask this Clock for "now" instead of calling Instant.now() directly.
 * Tests can then pass a fixed or shifted clock to check expiry logic (tokens, reset links)
 * without sleeping for hours.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
