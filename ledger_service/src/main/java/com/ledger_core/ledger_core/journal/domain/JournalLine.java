package com.ledger_core.ledger_core.journal.domain;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;

import java.util.Objects;

public record JournalLine(
        String accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        MonetaryAmount debitAmount,
        MonetaryAmount creditAmount,
        String memo,
        CashFlowCategory cashFlowCategory
) {
    public JournalLine {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(accountCode, "accountCode is required");
        Objects.requireNonNull(accountName, "accountName is required");
        Objects.requireNonNull(accountType, "accountType is required");

        debitAmount = debitAmount != null ? debitAmount : MonetaryAmount.ZERO;
        creditAmount = creditAmount != null ? creditAmount : MonetaryAmount.ZERO;
        cashFlowCategory = cashFlowCategory != null ? cashFlowCategory : CashFlowCategory.NONE;

        if (debitAmount.isNegative() || creditAmount.isNegative()) {
            throw new DomainException("JournalLine debit and credit amounts must be non-negative");
        }

        boolean hasDebit = debitAmount.isPositive();
        boolean hasCredit = creditAmount.isPositive();

        if (hasDebit && hasCredit) {
            throw new DomainException("JournalLine cannot have both debitAmount > 0 and creditAmount > 0");
        }
        if (!hasDebit && !hasCredit) {
            throw new DomainException("JournalLine must have either a positive debitAmount or a positive creditAmount");
        }
    }

    public JournalLine reversedCopy() {
        return new JournalLine(
                accountId,
                accountCode,
                accountName,
                accountType,
                creditAmount,
                debitAmount,
                "Reversal: " + (memo != null ? memo : ""),
                cashFlowCategory
        );
    }
}