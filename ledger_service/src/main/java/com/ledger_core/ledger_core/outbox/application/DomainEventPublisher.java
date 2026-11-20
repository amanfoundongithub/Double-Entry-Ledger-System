package com.ledger_core.ledger_core.outbox.application;

import com.ledger_core.ledger_core.journal.domain.JournalEntry;
import com.ledger_core.ledger_core.outbox.domain.OutboxEvent;
import com.ledger_core.ledger_core.outbox.infrastructure.OutboxEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
public class DomainEventPublisher {

    public static final String AGGREGATE_JOURNAL_ENTRY = "JOURNAL_ENTRY";
    public static final String EVENT_ENTRY_POSTED = "JournalEntryPostedEvent";
    public static final String EVENT_ENTRY_REVERSED = "JournalEntryReversedEvent";

    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DomainEventPublisher(
            OutboxEventRepository outboxRepository,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void enqueueJournalEntryPosted(JournalEntry entry) {
        enqueue(AGGREGATE_JOURNAL_ENTRY, entry.entryNumber(), EVENT_ENTRY_POSTED, buildPayload(entry));
    }

    public void enqueueJournalEntryReversed(JournalEntry reversalEntry) {
        enqueue(AGGREGATE_JOURNAL_ENTRY, reversalEntry.entryNumber(), EVENT_ENTRY_REVERSED, buildPayload(reversalEntry));
    }

    public void enqueue(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent event = OutboxEvent.pending(
                aggregateType,
                aggregateId,
                eventType,
                payload,
                Instant.now(clock)
        );
        outboxRepository.save(event);
    }

    private String buildPayload(JournalEntry entry) {
        try {
            Map<String, Object> body = Map.of(
                    "entryId", entry.id() != null ? entry.id() : "",
                    "entryNumber", entry.entryNumber(),
                    "status", entry.status().name(),
                    "periodId", entry.periodId(),
                    "total", entry.totalAmount().value().toPlainString(),
                    "postingDate", entry.postingDate().toString()
            );
            return jsonMapper.writeValueAsString(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}