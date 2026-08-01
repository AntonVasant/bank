package com.delivery.service.delivery.reconciliation;

import com.delivery.service.delivery.config.BankRestEndpointProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delivers a bank's reconciliation request over REST (not gRPC) — the bank
 * only needs to acknowledge receipt here; its own comparison result is
 * reported back asynchronously via {@code POST /api/reconciliation/result}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankReconciliationRestClient {

    private final BankRestEndpointProperties endpointProperties;
    private final RestClient.Builder restClientBuilder;

    public void sendReconciliationRequest(ReconciliationRequest request) {
        BankRestEndpointProperties.Endpoint endpoint = endpointProperties.getEndpoints().get(request.bankCode());

        if (endpoint == null) {
            log.warn("No REST endpoint configured for bank='{}'; skipping reconciliation request for cycleId='{}'",
                    request.bankCode(), request.cycleId());
            return;
        }

        log.debug("Sending reconciliation request to bank='{}' baseUrl='{}' cycleId='{}'",
                request.bankCode(), endpoint.getBaseUrl(), request.cycleId());

        restClientBuilder.build()
                .post()
                .uri(endpoint.getBaseUrl() + "/api/reconciliation")
                .body(request)
                .retrieve()
                .toBodilessEntity();

        log.info("Reconciliation request accepted by bank='{}' cycleId='{}' currency='{}'",
                request.bankCode(), request.cycleId(), request.currency());
    }
}
