package com.ledger_core.ledger_core.journal.infrastructure;

import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.journal.domain.JournalEntry;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(collection = "journal_entries")
@CompoundIndexes({
        @CompoundIndex(name = "idx_posting_date_status", def = "{'postingDate': 1, 'status': 1}"),
        @CompoundIndex(name = "idx_account_posting_date", def = "{'lines.accountId': 1, 'postingDate': 1}")
})
public record JournalEntryDocument(
        @Id String id,
        @Indexed(unique = true) String entryNumber,
        String reference,
        String sourceType,
        EntryStatus status,
        Instant postingDate,
        Instant transactionDate,
        String description,
        @Indexed String periodId,
        String reversalOfEntryId,
        String reversedByEntryId,
        @Indexed(unique = true) String idempotencyKey,
        List<JournalLineDocument> lines,
        BigDecimal totalAmount,
        Instant createdAt,
        String createdBy
) {
    public static JournalEntryDocument fromDomain(JournalEntry entry) {
        return new JournalEntryDocument(
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
                entry.lines().stream().map(JournalLineDocument::fromDomain).toList(),
                entry.totalAmount().value(),
                entry.createdAt(),
                entry.createdBy()
        );
    }

    public JournalEntry toDomain() {
        return new JournalEntry(
                id,
                entryNumber,
                reference,
                sourceType,
                status,
                postingDate,
                transactionDate,
                description,
                periodId,
                reversalOfEntryId,
                reversedByEntryId,
                idempotencyKey,
                lines.stream().map(JournalLineDocument::toDomain).toList(),
                MonetaryAmount.of(totalAmount),
                createdAt,
                createdBy
        );
    }
}