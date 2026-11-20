package com.ledger_core.ledger_core.reporting.api;

import com.ledger_core.ledger_core.accounts.domain.AccountType;

import java.math.BigDecimal;

public record StatementLine(
        String accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        boolean contra,
        BigDecimal amount
) {
}