package com.ledger_core.ledger_core.journal.api;

import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.journal.domain.JournalEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record JournalEntryResponse(
        String id,
        String entryNumber,
        String reference,
        String sourceType,
        EntryStatus status,
        Instant postingDate,
        Instant transactionDate,
        String description,
        String periodId,
        String reversalOfEntryId,
        String reversedByEntryId,
        String idempotencyKey,
        List<JournalLineResponse> lines,
        BigDecimal totalAmount,
        Instant createdAt,
        String createdBy
) {
    public static JournalEntryResponse fromDomain(JournalEntry entry) {
        return new JournalEntryResponse(
                entry.id(),
                entry.entryNumber(),
                entry.reference(),
                entry.sourceType(),
                entry.status(),
                entry.postingDate(),
                entry.transactionDate(),
                entry.description(),
                entry.periodId(),
                entry.reversalOfEntryId(),
                entry.reversedByEntryId(),
                entry.idempotencyKey(),
                entry.lines().stream().map(JournalLineResponse::fromDomain).toList(),
                entry.totalAmount().value(),
                entry.createdAt(),
                entry.createdBy()
        );
    }
}