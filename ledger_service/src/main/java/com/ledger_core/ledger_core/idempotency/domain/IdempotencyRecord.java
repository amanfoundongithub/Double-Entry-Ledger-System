package com.ledger_core.ledger_core.idempotency.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "idempotency_records")
public record IdempotencyRecord(
        @Id String id,
        @Indexed(unique = true) String idempotencyKey,
        String requestHash,
        int responseStatusCode,
        String responsePayload,
        @Indexed(expireAfter = "86400s") Instant createdAt
) {
    public IdempotencyRecord {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(requestHash, "requestHash is required");
        Objects.requireNonNull(responsePayload, "responsePayload is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}