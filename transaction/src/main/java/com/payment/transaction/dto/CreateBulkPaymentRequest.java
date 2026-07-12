package com.payment.transaction.dto;

import java.util.List;

public record CreateBulkPaymentRequest(
        String batchReference,
        String description,
        List<BulkPaymentItem> payments) {}
