package com.payment.transaction.dto;

public record SettlementRailResponse(
        String settlementReference,
        String status   // ACCEPTED | REJECTED
) {}
