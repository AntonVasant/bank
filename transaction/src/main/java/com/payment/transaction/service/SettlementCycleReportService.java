package com.payment.transaction.service;

import com.payment.transaction.entity.SettlementCycleRecord;
import com.payment.transaction.repository.SettlementCycleRecordRepository;
import com.settlement.rail.grpc.SettlementCycleReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Records the settlement cycle net position reported by the rail, upserting
 * by (cycleId, currency) so a retried gRPC call doesn't create duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCycleReportService {

    private final SettlementCycleRecordRepository settlementCycleRecordRepository;

    @Transactional
    public SettlementCycleRecord recordReport(SettlementCycleReport report) {
        SettlementCycleRecord record = settlementCycleRecordRepository
                .findByCycleIdAndCurrency(report.getCycleId(), report.getCurrency())
                .orElseGet(SettlementCycleRecord::new);

        record.setCycleId(report.getCycleId());
        record.setBankCode(report.getBankCode());
        record.setCurrency(report.getCurrency());
        record.setNetAmount(new BigDecimal(report.getNetAmount()));
        record.setTransactionCount(report.getTransactionCount());
        record.setGeneratedAt(LocalDateTime.ofInstant(Instant.parse(report.getGeneratedAt()), ZoneOffset.UTC));

        SettlementCycleRecord saved = settlementCycleRecordRepository.save(record);
        log.info("Recorded settlement cycle report cycleId='{}' bankCode='{}' currency='{}' netAmount={} transactionCount={}",
                saved.getCycleId(), saved.getBankCode(), saved.getCurrency(), saved.getNetAmount(), saved.getTransactionCount());
        return saved;
    }
}
