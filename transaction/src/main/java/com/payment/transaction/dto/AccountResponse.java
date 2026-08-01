package com.payment.transaction.dto;

public record AccountResponse(Long id, String accountNumber, String status, Long userId) {}
