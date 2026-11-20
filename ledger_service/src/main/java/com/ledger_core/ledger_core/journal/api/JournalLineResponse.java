package com.ledger_core.ledger_core.journal.api;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.journal.domain.JournalLine;

import java.math.BigDecimal;

public record JournalLineResponse(
        String accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String memo,
        CashFlowCategory cashFlowCategory
) {
    public static JournalLineResponse fromDomain(JournalLine line) {
        return new JournalLineResponse(
                line.accountId(),
                line.accountCode(),
                line.accountName(),
                line.accountType(),
                line.debitAmount().value(),
                line.creditAmount().value(),
                line.memo(),
                line.cashFlowCategory()
        );
    }
}