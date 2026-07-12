package com.payment.transaction.dto;

public record AccountResponse(Long accountId, String accountNumber, String status, Long userId) {}
