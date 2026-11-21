package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountLedgerResponse(
        String accountId,
        String accountCode,
        String accountName,
        String accountType,
        String normalSide,
        boolean contra,
        Instant startDate,
        Instant endDate,
        BigDecimal openingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance,
        List<AccountLedgerEntryLine> entries
) {}