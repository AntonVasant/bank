package com.delivery.service.delivery.settlement;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps the JSON payload published by the rail's outbox for the
 * {@code SETTLEMENT_NET_POSITION_CALCULATED} event type
 * (topic {@code settlement.net-position.events}).
 */
public record SettlementNetPositionEvent(
        @JsonProperty("positionId") String positionId,
        @JsonProperty("cycleId") String cycleId,
        @JsonProperty("bankCode") String bankCode,
        @JsonProperty("currency") String currency,
        @JsonProperty("netAmount") String netAmount,
        @JsonProperty("transactionCount") int transactionCount,
        @JsonProperty("generatedAt") String generatedAt) {
}
