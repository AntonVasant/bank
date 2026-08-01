package com.delivery.service.delivery.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the gRPC host/port for each bank's {@code SettlementReportService}
 * endpoint, keyed by bank code. Used to deliver each settlement cycle's net
 * position (consumed from Kafka) to the owning bank. Populated from:
 * <pre>
 * banks:
 *   grpc:
 *     endpoints:
 *       HDFC_IN:
 *         host: localhost
 *         port: 9091
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
