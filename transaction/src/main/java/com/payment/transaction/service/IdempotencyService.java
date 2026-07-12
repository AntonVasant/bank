package com.payment.transaction.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final String KEY_PREFIX = "idem:";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PAYMENT_ID = "paymentId";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final Duration TTL = Duration.ofHours(24);

    public enum IdempotencyStatus {
        IN_PROGRESS, COMPLETE
    }

    private final HashOperations<String, String, String> hashOps;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }

    /**
     * Check whether an idempotency key already exists.
     * - If COMPLETE  → returns the cached response JSON.
     * - If IN_PROGRESS → throws 409 Conflict (duplicate in-flight request).
     * - If absent     → returns empty (caller should proceed and call {@link #register}).
     */
    public <T> Optional<T> checkExisting(String key, Class<T> responseType) {
        String redisKey = KEY_PREFIX + key;
        Map<String, String> hash = hashOps.entries(redisKey);

        if (hash.isEmpty()) {
            return Optional.empty();
        }

        String status = hash.get(FIELD_STATUS);

        if (IdempotencyStatus.IN_PROGRESS.name().equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Duplicate request: idempotency key '" + key + "' is already being processed.");
        }

        if (IdempotencyStatus.COMPLETE.name().equals(status)) {
            String cachedResponse = hash.get(FIELD_RESPONSE);
            try {
                return Optional.of(objectMapper.readValue(cachedResponse, responseType));
            } catch (JacksonException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to deserialize cached response for key: " + key);
            }
        }

        return Optional.empty();
    }

    /**
     * Register a new idempotency key with IN_PROGRESS status.
     * Must be called before processing begins.
     */
    public void register(String key, Long entityId) {
        String redisKey = KEY_PREFIX + key;
        Map<String, String> hash = Map.of(
                FIELD_STATUS, IdempotencyStatus.IN_PROGRESS.name(),
                FIELD_PAYMENT_ID, entityId != null ? String.valueOf(entityId) : "",
                FIELD_RESPONSE, "",
                FIELD_CREATED_AT, Instant.now().toString()
        );
        hashOps.putAll(redisKey, hash);
        redisTemplate.expire(redisKey, TTL);
    }

    /**
     * Mark an idempotency key as COMPLETE and store the serialized response.
     * Must be called after successful processing.
     */
    public <T> void markComplete(String key, Long entityId, T response) {
        String redisKey = KEY_PREFIX + key;
        String responseJson = objectMapper.writeValueAsString(response);
        hashOps.put(redisKey, FIELD_STATUS, IdempotencyStatus.COMPLETE.name());
        hashOps.put(redisKey, FIELD_PAYMENT_ID, String.valueOf(entityId));
        hashOps.put(redisKey, FIELD_RESPONSE, responseJson);
        redisTemplate.expire(redisKey, TTL);
    }
}
