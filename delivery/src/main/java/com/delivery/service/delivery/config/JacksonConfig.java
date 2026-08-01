package com.delivery.service.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's auto-configured Jackson bean is for the Jackson 3 API
 * ({@code tools.jackson.databind.ObjectMapper}). Some code in this module
 * (e.g. Kafka listeners) still uses the classic Jackson 2 API
 * ({@code com.fasterxml.jackson.databind.ObjectMapper}), which is on the
 * classpath but has no auto-configured bean — so it's defined here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
