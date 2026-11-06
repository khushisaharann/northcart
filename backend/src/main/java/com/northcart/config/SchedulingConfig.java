package com.northcart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled methods (e.g. OrderExpiryScheduler). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
