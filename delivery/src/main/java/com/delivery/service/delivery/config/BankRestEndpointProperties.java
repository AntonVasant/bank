package com.delivery.service.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the REST base URL for each bank's reconciliation endpoint
 * ({@code POST /api/reconciliation}), keyed by bank code. Populated from:
 * <pre>
 * banks:
 *   rest:
 *     endpoints:
 *       HDFC_IN:
 *         base-url: http://localhost:8081
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "banks.rest")
@Getter
@Setter
public class BankRestEndpointProperties {

    private Map<String, Endpoint> endpoints = new HashMap<>();

    @Getter
    @Setter
    public static class Endpoint {
        private String baseUrl;
    }
}
