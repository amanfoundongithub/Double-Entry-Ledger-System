package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountLedgerEntryLine(
        String journalEntryId,
        String entryNumber,
        String reference,
        String entryStatus,
        Instant postingDate,
        Instant transactionDate,
        String periodId,
        String description,
        String memo,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal runningBalance
) {}