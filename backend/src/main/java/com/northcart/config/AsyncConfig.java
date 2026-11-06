package com.northcart.config;

import com.northcart.common.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;

/**
 * Enables @Async. Spring Boot then auto-configures a ThreadPoolTaskExecutor
 * (tuned via spring.task.execution.* in application.yml) and applies the TaskDecorator below.
 *
 * <p>Used for sending emails and refunds so a slow third party never slows down an API response.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * MDC is thread-local, so an @Async email would log without the request id.
     * This copies the caller's MDC into the worker thread for the duration of the task.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    task.run();
                } finally {
                    MDC.remove(RequestIdFilter.MDC_KEY);
                }
            };
        };
    }
}
