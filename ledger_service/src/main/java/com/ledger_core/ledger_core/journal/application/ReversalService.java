package com.ledger_core.ledger_core.journal.application;

import com.ledger_core.ledger_core.journal.api.JournalEntryResponse;
import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.journal.domain.JournalEntry;
import com.ledger_core.ledger_core.journal.domain.JournalLine;
import com.ledger_core.ledger_core.journal.infrastructure.JournalEntryDocument;
import com.ledger_core.ledger_core.journal.infrastructure.JournalEntryRepository;
import com.ledger_core.ledger_core.outbox.application.DomainEventPublisher;
import com.ledger_core.ledger_core.periods.application.PeriodEnforcementService;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ReversalService {

    private final JournalEntryRepository journalEntryRepository;
    private final PeriodEnforcementService periodEnforcementService;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ReversalService(
            JournalEntryRepository journalEntryRepository,
            PeriodEnforcementService periodEnforcementService,
            DomainEventPublisher domainEventPublisher,
            Clock clock
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.periodEnforcementService = periodEnforcementService;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public JournalEntryResponse reverseEntry(String originalEntryId, String reason, String idempotencyKey, String createdBy) {
        JournalEntryDocument originalDoc = journalEntryRepository.findById(originalEntryId)
                .orElseThrow(() -> new DomainException("Original journal entry not found with id: " + originalEntryId));

        JournalEntry original = originalDoc.toDomain();
        if (original.status() == EntryStatus.REVERSED) {
            throw new DomainException("Journal entry '" + original.entryNumber() + "' is already reversed");
        }

        Instant now = Instant.now(clock);
        periodEnforcementService.assertPostingAllowed(original.periodId(), now);

        int year = now.atZone(ZoneOffset.UTC).getYear();
        String reversalNumber = String.format("JE-%04d-%06d", year, journalEntryRepository.count() + 1);

        List<JournalLine> reversedLines = original.lines().stream()
                .map(JournalLine::reversedCopy)
                .toList();

        JournalEntry reversalEntry = new JournalEntry(
                null,
                reversalNumber,
                original.reference(),
                "REVERSAL",
                EntryStatus.POSTED,
                now,
                now,
                reason != null && !reason.isBlank()
                        ? reason.trim()
                        : "Reversal of " + original.entryNumber(),
                original.periodId(),
                original.id(),
                null,
                idempotencyKey.trim(),
                reversedLines,
                MonetaryAmount.ZERO,
                now,
                createdBy != null ? createdBy : "system"
        );

        JournalEntryDocument savedReversal = journalEntryRepository.save(JournalEntryDocument.fromDomain(reversalEntry));
        JournalEntry persistedReversal = savedReversal.toDomain();

        JournalEntry updatedOriginal = original.markReversed(savedReversal.id());
        journalEntryRepository.save(JournalEntryDocument.fromDomain(updatedOriginal));

        domainEventPublisher.enqueueJournalEntryReversed(persistedReversal);

        return JournalEntryResponse.fromDomain(persistedReversal);
    }
}