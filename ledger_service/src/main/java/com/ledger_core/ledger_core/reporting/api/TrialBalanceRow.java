package com.ledger_core.ledger_core.reporting.api;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;

import java.math.BigDecimal;

public record TrialBalanceRow(
        String accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        NormalSide normalSide,
        boolean contra,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal netBalance
) {
}