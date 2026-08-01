package com.payment.transaction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed cache of the settlement rail's currently-open cycle id.
 * <p>
 * Populated in real time by {@link SettlementCycleListener} consuming the
 * rail's cycle-open Kafka broadcast, so a payment can read the cycle id it
 * should tag its ledger hold with instantly, without asking the rail
 * per-payment. If the rail's cycle rotates in the (small) window between
 * broadcast and a payment landing, the rail rejects with the real cycle id
 * (see {@code PaymentOrchestrator}'s cycle-mismatch retry), which is used to
 * correct this cache immediately.
 */
@Slf4j
@Service
public class CycleCacheService {

    private static final String CURRENT_CYCLE_KEY = "settlement:cycle:current";

    private final StringRedisTemplate redisTemplate;

    public CycleCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return the cached currently-open cycle id, or 0 if none has been
     *         broadcast/cached yet (e.g. this bank just started up before
     *         the rail's next cycle rotation) — untagged rather than guessed.
     */
    public long getCurrentCycleId() {
        String value = redisTemplate.opsForValue().get(CURRENT_CYCLE_KEY);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Cached cycle id '{}' is not numeric; treating as unknown", value);
            return 0L;
        }
    }

    /** Caches {@code cycleId} as the currently-open cycle, from either the rail's broadcast or a cycle-mismatch rejection. */
    public void setCurrentCycleId(long cycleId) {
        redisTemplate.opsForValue().set(CURRENT_CYCLE_KEY, String.valueOf(cycleId));
    }
}
