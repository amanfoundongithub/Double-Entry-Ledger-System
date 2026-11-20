package com.ledger_core.ledger_core.journal.infrastructure;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.journal.domain.JournalLine;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;

import java.math.BigDecimal;

public record JournalLineDocument(
        String accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String memo,
        CashFlowCategory cashFlowCategory
) {
    public static JournalLineDocument fromDomain(JournalLine line) {
        return new JournalLineDocument(
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

    public JournalLine toDomain() {
        return new JournalLine(
                accountId,
                accountCode,
                accountName,
                accountType,
                MonetaryAmount.of(debitAmount),
                MonetaryAmount.of(creditAmount),
                memo,
                cashFlowCategory
        );
    }
}