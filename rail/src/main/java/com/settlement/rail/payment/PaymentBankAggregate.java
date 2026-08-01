package com.settlement.rail.payment;

import java.math.BigDecimal;

/**
 * Projection for the sum of payment amounts grouped by bank and currency for
 * a settlement cycle (used to compute each bank's net settlement position).
 * One aggregate row represents either the debit side ({@code fromBank}) or
 * the credit side ({@code toBank}) of every {@link Payment} in the cycle —
 * see {@link PaymentRepository#aggregateDebitsByBankForCycle} and
 * {@link PaymentRepository#aggregateCreditsByBankForCycle}.
 */
public interface PaymentBankAggregate {

    Long getBankId();

    String getBankCode();

    String getCurrency();

    BigDecimal getTotalAmount();

    Long getEntryCount();
}
