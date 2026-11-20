package com.ledger_core.ledger_core.outbox.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "outbox_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_outbox_status_created", def = "{'status': 1, 'createdAt': 1}")
})
public class OutboxEvent {

    @Id
    private String id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private int retryCount;
    private Instant createdAt;
    private Instant lastAttemptedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            OutboxStatus status,
            int retryCount,
            Instant createdAt,
            Instant lastAttemptedAt
    ) {
        this.id = id;
        this.aggregateType = requireNonBlank(aggregateType, "aggregateType");
        this.aggregateId = requireNonBlank(aggregateId, "aggregateId");
        this.eventType = requireNonBlank(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.retryCount = Math.max(0, retryCount);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.lastAttemptedAt = lastAttemptedAt;
    }

    public static OutboxEvent pending(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant createdAt
    ) {
        return new OutboxEvent(
                null,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                OutboxStatus.PENDING,
                0,
                createdAt,
                null
        );
    }

    public void markPublished(Instant attemptedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.lastAttemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt is required");
    }

    public void recordFailure(int maxRetries, Instant attemptedAt) {
        this.retryCount += 1;
        this.lastAttemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt is required");
        this.status = this.retryCount >= maxRetries ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }
}