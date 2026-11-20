package com.ledger_core.ledger_core.idempotency.application;

import com.ledger_core.ledger_core.idempotency.domain.IdempotencyRecord;
import com.ledger_core.ledger_core.idempotency.infrastructure.IdempotencyRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public String hashPayload(String rawJson) {
        Objects.requireNonNull(rawJson, "rawJson is required");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Optional<IdempotencyRecord> checkExisting(String idempotencyKey, String requestHash) {
        validateKey(idempotencyKey);
        return repository.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.requestHash().equals(requestHash)) {
                throw new DomainException(
                        "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload"
                );
            }
            return existing;
        });
    }

    public void recordResponse(String idempotencyKey, String requestHash, int statusCode, String responsePayload) {
        validateKey(idempotencyKey);
        IdempotencyRecord record = new IdempotencyRecord(
                null,
                idempotencyKey.trim(),
                requestHash,
                statusCode,
                responsePayload,
                Instant.now(clock)
        );
        repository.save(record);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainException("Idempotency-Key header is required");
        }
    }
}