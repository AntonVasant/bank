package com.payment.transaction.grpc;

import com.payment.transaction.service.CycleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumes the rail's cycle-open broadcast ({@code settlement.cycle.opened.events},
 * payload {@code {"cycleId":123}}) and caches the cycle id in Redis via
 * {@link CycleCacheService} — see the "Cycle Broadcast" design: every
 * commercial bank learns the currently open cycle id in real time, instead
 * of asking the rail for it on every payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCycleListener {

    private static final Pattern CYCLE_ID_PATTERN = Pattern.compile("\"cycleId\"\\s*:\\s*(\\d+)");

    private final CycleCacheService cycleCacheService;

    @KafkaListener(topics = "settlement.cycle.opened.events", groupId = "${spring.kafka.consumer.group-id}")
    public void onCycleOpened(String payload) {
        Matcher matcher = CYCLE_ID_PATTERN.matcher(payload);
        if (!matcher.find()) {
            log.warn("Could not parse cycleId out of cycle-opened event payload: {}", payload);
            return;
        }
        long cycleId = Long.parseLong(matcher.group(1));
        cycleCacheService.setCurrentCycleId(cycleId);
        log.info("Cached new settlement cycle id={} from rail broadcast", cycleId);
    }
}
