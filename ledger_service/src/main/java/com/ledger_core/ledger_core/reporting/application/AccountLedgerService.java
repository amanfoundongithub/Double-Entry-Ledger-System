package com.ledger_core.ledger_core.reporting.application;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.journal.infrastructure.JournalEntryDocument;
import com.ledger_core.ledger_core.journal.infrastructure.JournalEntryRepository;
import com.ledger_core.ledger_core.journal.infrastructure.JournalLineDocument;
import com.ledger_core.ledger_core.reporting.api.AccountLedgerEntryLine;
import com.ledger_core.ledger_core.reporting.api.AccountLedgerResponse;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountLedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public AccountLedgerService(
            AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    public AccountLedgerResponse getAccountLedger(
            String accountId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endDate must not be before startDate"
            );
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found: " + accountId
                ));

        Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = endDate.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        boolean debitIncreases = increasesOnDebit(
                account.getNormalSide().name(),
                account.isContra()
        );

        List<JournalEntryDocument> entries = journalEntryRepository.findAll();

        BigDecimal openingBalance = zeroScaled();
        List<RawLine> inRangeLines = new ArrayList<>();
        BigDecimal totalDebits = zeroScaled();
        BigDecimal totalCredits = zeroScaled();

        for (JournalEntryDocument entry : entries) {
            if (entry.status() != EntryStatus.POSTED) {
                continue;
            }
            Instant effectiveDate = entry.postingDate();
            if (effectiveDate == null || entry.lines() == null) {
                continue;
            }

            for (JournalLineDocument line : entry.lines()) {
                if (!accountId.equals(line.accountId())) {
                    continue;
                }

                BigDecimal debit = toBigDecimal(line.debitAmount());
                BigDecimal credit = toBigDecimal(line.creditAmount());

                if (effectiveDate.isBefore(startInstant)) {
                    openingBalance = openingBalance.add(signedDelta(debit, credit, debitIncreases));
                } else if (!effectiveDate.isAfter(endInstant)) {
                    inRangeLines.add(new RawLine(entry, line, debit, credit));
                }
            }
        }

        inRangeLines.sort(
                Comparator.comparing((RawLine r) -> r.entry().postingDate())
                        .thenComparing(r -> r.entry().createdAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(r -> r.entry().entryNumber(), Comparator.nullsLast(Comparator.naturalOrder()))
        );

        BigDecimal running = openingBalance;
        List<AccountLedgerEntryLine> resultLines = new ArrayList<>(inRangeLines.size());

        for (RawLine raw : inRangeLines) {
            totalDebits = totalDebits.add(raw.debit());
            totalCredits = totalCredits.add(raw.credit());
            running = running.add(signedDelta(raw.debit(), raw.credit(), debitIncreases));

            JournalEntryDocument je = raw.entry();
            JournalLineDocument jl = raw.line();

            resultLines.add(new AccountLedgerEntryLine(
                    je.id(),
                    je.entryNumber(),
                    je.reference(),
                    je.status().name(),
                    je.postingDate(),
                    je.transactionDate(),
                    je.periodId(),
                    je.description(),
                    jl.memo(),
                    raw.debit(),
                    raw.credit(),
                    running
            ));
        }

        return new AccountLedgerResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType().name(),
                account.getNormalSide().name(),
                account.isContra(),
                startInstant,
                endInstant,
                openingBalance,
                totalDebits,
                totalCredits,
                running,
                resultLines
        );
    }

    private boolean increasesOnDebit(String normalSide, boolean contra) {
        boolean debitNormal = "DEBIT".equalsIgnoreCase(normalSide);
        return contra ? !debitNormal : debitNormal;
    }

    private BigDecimal signedDelta(BigDecimal debit, BigDecimal credit, boolean debitIncreases) {
        return debitIncreases ? debit.subtract(credit) : credit.subtract(debit);
    }

    private BigDecimal toBigDecimal(BigDecimal amount) {
        if (amount == null) {
            return zeroScaled();
        }
        return amount.setScale(MonetaryAmount.SCALE, MonetaryAmount.ROUNDING_MODE);
    }

    private BigDecimal zeroScaled() {
        return MonetaryAmount.ZERO.value();
    }

    private record RawLine(
            JournalEntryDocument entry,
            JournalLineDocument line,
            BigDecimal debit,
            BigDecimal credit
    ) {}
}