package com.ledger_core.ledger_core.accounts.api;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;

import java.time.Instant;

public record AccountResponse(
        String id,
        String code,
        String name,
        AccountType type,
        NormalSide normalSide,
        CashFlowCategory cashFlowCategory,
        boolean cashEquivalent,
        boolean contra,
        String currency,
        boolean active,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getNormalSide(),
                account.getCashFlowCategory(),
                account.isCashEquivalent(),
                account.isContra(),
                account.getCurrency(),
                account.isActive(),
                account.getDescription(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}