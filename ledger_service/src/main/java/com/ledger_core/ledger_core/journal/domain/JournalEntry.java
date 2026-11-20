package com.ledger_core.ledger_core.journal.domain;

import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.ImbalancedEntryException;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record JournalEntry(
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
        List<JournalLine> lines,
        MonetaryAmount totalAmount,
        Instant createdAt,
        String createdBy
) {
    public JournalEntry {
        Objects.requireNonNull(entryNumber, "entryNumber is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(postingDate, "postingDate is required");
        Objects.requireNonNull(transactionDate, "transactionDate is required");
        Objects.requireNonNull(periodId, "periodId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(lines, "lines are required");

        if (lines.size() < 2) {
            throw new DomainException("JournalEntry must contain at least 2 lines");
        }

        lines = List.copyOf(lines);

        MonetaryAmount computedDebits = totalDebits(lines);
        MonetaryAmount computedCredits = totalCredits(lines);

        if (computedDebits.compareTo(computedCredits) != 0) {
            throw new ImbalancedEntryException(computedDebits.value(), computedCredits.value());
        }

        totalAmount = computedDebits;
    }

    public static MonetaryAmount totalDebits(List<JournalLine> lines) {
        return lines.stream()
                .map(JournalLine::debitAmount)
                .reduce(MonetaryAmount.ZERO, MonetaryAmount::add);
    }

    public static MonetaryAmount totalCredits(List<JournalLine> lines) {
        return lines.stream()
                .map(JournalLine::creditAmount)
                .reduce(MonetaryAmount.ZERO, MonetaryAmount::add);
    }

    public JournalEntry markReversed(String reversalEntryId) {
        Objects.requireNonNull(reversalEntryId, "reversalEntryId is required");
        if (this.status == EntryStatus.REVERSED) {
            throw new DomainException("JournalEntry '" + this.entryNumber + "' is already reversed");
        }
        return new JournalEntry(
                this.id,
                this.entryNumber,
                this.reference,
                this.sourceType,
                EntryStatus.REVERSED,
                this.postingDate,
                this.transactionDate,
                this.description,
                this.periodId,
                this.reversalOfEntryId,
                reversalEntryId,
                this.idempotencyKey,
                this.lines,
                this.totalAmount,
                this.createdAt,
                this.createdBy
        );
    }
}