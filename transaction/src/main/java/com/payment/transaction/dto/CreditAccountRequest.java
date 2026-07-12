package com.payment.transaction.dto;

import java.math.BigDecimal;

public record CreditAccountRequest(BigDecimal amount) {}
