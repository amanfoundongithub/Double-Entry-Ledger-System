package com.ledger_core.ledger_core.journal.application;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.journal.api.JournalEntryResponse;
import com.ledger_core.ledger_core.journal.api.PostJournalEntryRequest;
import com.ledger_core.ledger_core.journal.api.PostJournalLineRequest;
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
public class JournalPostingService {

    private final JournalEntryRepository journalEntryRepository;
    private final PeriodEnforcementService periodEnforcementService;
    private final AccountRepository accountRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public JournalPostingService(
            JournalEntryRepository journalEntryRepository,
            PeriodEnforcementService periodEnforcementService,
            AccountRepository accountRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.periodEnforcementService = periodEnforcementService;
        this.accountRepository = accountRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public JournalEntryResponse postEntry(PostJournalEntryRequest request, String idempotencyKey) {
        Instant now = Instant.now(clock);
        Instant postingDate = request.postingDate() != null ? request.postingDate() : now;
        Instant transactionDate = request.transactionDate() != null ? request.transactionDate() : postingDate;
        String periodId = resolvePeriodId(request.periodId(), postingDate);
        periodEnforcementService.assertPostingAllowed(periodId, postingDate);

        List<JournalLine> domainLines = request.lines().stream()
                .map(lineReq -> toDomainLine(lineReq, now))
                .toList();

        String entryNumber = generateEntryNumber(postingDate);

        JournalEntry entry = new JournalEntry(
                null,
                entryNumber,
                request.reference(),
                request.sourceType() != null ? request.sourceType() : "MANUAL_JOURNAL",
                EntryStatus.POSTED,
                postingDate,
                transactionDate,
                request.description(),
                periodId,
                null,
                null,
                idempotencyKey.trim(),
                domainLines,
                MonetaryAmount.ZERO, // recalculated and verified by JournalEntry constructor
                now,
                request.createdBy() != null ? request.createdBy() : "system"
        );

        JournalEntryDocument saved = journalEntryRepository.save(JournalEntryDocument.fromDomain(entry));
        JournalEntry persistedDomain = saved.toDomain();
        domainEventPublisher.enqueueJournalEntryPosted(persistedDomain);
        return JournalEntryResponse.fromDomain(persistedDomain);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getById(String id) {
        return journalEntryRepository.findById(id)
                .map(doc -> JournalEntryResponse.fromDomain(doc.toDomain()))
                .orElseThrow(() -> new DomainException("Journal entry not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getByEntryNumber(String entryNumber) {
        return journalEntryRepository.findByEntryNumber(entryNumber)
                .map(doc -> JournalEntryResponse.fromDomain(doc.toDomain()))
                .orElseThrow(() -> new DomainException("Journal entry not found with number: " + entryNumber));
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> listByAccount(String accountId) {
        return journalEntryRepository.findByLinesAccountIdOrderByPostingDateDesc(accountId).stream()
                .map(doc -> JournalEntryResponse.fromDomain(doc.toDomain()))
                .toList();
    }

    private JournalLine toDomainLine(PostJournalLineRequest lineReq, Instant now) {
        Account account = resolveAccount(lineReq);
        if (!account.isActive()) {
            throw new DomainException("Cannot post to inactive account '" + account.getCode() + "'");
        }

        MonetaryAmount debit = lineReq.debitAmount() != null ? MonetaryAmount.of(lineReq.debitAmount()) : MonetaryAmount.ZERO;
        MonetaryAmount credit = lineReq.creditAmount() != null ? MonetaryAmount.of(lineReq.creditAmount()) : MonetaryAmount.ZERO;

        CashFlowCategory cfCategory = lineReq.cashFlowCategory() != null
                ? lineReq.cashFlowCategory()
                : account.getCashFlowCategory();

        return new JournalLine(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                debit,
                credit,
                lineReq.memo(),
                cfCategory
        );
    }

    private Account resolveAccount(PostJournalLineRequest lineReq) {
        if (lineReq.accountId() != null && !lineReq.accountId().isBlank()) {
            return accountRepository.findById(lineReq.accountId().trim())
                    .orElseThrow(() -> new DomainException("Account not found with id: " + lineReq.accountId()));
        }
        if (lineReq.accountCode() != null && !lineReq.accountCode().isBlank()) {
            return accountRepository.findByCode(lineReq.accountCode().trim())
                    .orElseThrow(() -> new DomainException("Account not found with code: " + lineReq.accountCode()));
        }
        throw new DomainException("Each journal line must specify either accountId or accountCode");
    }

    private String resolvePeriodId(String requestedPeriodId, Instant postingDate) {
        if (requestedPeriodId != null && !requestedPeriodId.isBlank()) {
            return requestedPeriodId.trim();
        }
        var localDate = postingDate.atZone(ZoneOffset.UTC).toLocalDate();
        return String.format("%04d-%02d", localDate.getYear(), localDate.getMonthValue());
    }

    private String generateEntryNumber(Instant postingDate) {
        int year = postingDate.atZone(ZoneOffset.UTC).getYear();
        long sequence = journalEntryRepository.count() + 1;
        return String.format("JE-%04d-%06d", year, sequence);
    }
}