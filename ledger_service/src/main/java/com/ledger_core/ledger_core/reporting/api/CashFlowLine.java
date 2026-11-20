package com.ledger_core.ledger_core.reporting.api;

import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;

import java.math.BigDecimal;

public record CashFlowLine(
        String accountId,
        String accountCode,
        String accountName,
        CashFlowCategory category,
        BigDecimal amount
) {
}