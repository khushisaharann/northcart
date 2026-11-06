package com.northcart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI metadata. Endpoints are discovered automatically from controllers;
 * this class only adds the title/description and the JWT "Authorize" button
 * (used from Phase 4 with @SecurityRequirement(name = "bearerAuth")).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI northCartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NorthCart API")
                        .version("v1")
                        .description("REST API for the NorthCart e-commerce platform")
                        .contact(new Contact().name("NorthCart")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
