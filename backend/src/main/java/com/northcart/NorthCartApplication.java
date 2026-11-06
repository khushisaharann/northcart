package com.northcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. @SpringBootApplication = @Configuration + @EnableAutoConfiguration
 * + @ComponentScan of this package and everything below it (com.northcart.**).
 * That is why this class sits in the root package.
 */
@SpringBootApplication
@ConfigurationPropertiesScan // picks up @ConfigurationProperties records such as AppProperties
public class NorthCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(NorthCartApplication.class, args);
    }
}
