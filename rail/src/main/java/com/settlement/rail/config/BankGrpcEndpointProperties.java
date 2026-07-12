package com.settlement.rail.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds gRPC host/port for each receiver bank, keyed by bank code.
 * Populated from:
 * <pre>
 * banks:
 *   grpc:
 *     endpoints:
 *       HDFC_IN:
 *         host: localhost
 *         port: 9091
 *       ICICI_IN:
 *         host: localhost
 *         port: 9092
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "banks.grpc")
@Getter
@Setter
public class BankGrpcEndpointProperties {

    private Map<String, Endpoint> endpoints = new HashMap<>();

    @Getter
    @Setter
    public static class Endpoint {
        private String host;
        private int port;
    }
}
