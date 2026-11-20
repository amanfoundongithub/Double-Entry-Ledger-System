package com.ledger_core.ledger_core.accounts.api;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank(message = "Account code is required")
        @Size(max = 20, message = "Account code must be at most 20 characters")
        String code,

        @NotBlank(message = "Account name is required")
        @Size(max = 120, message = "Account name must be at most 120 characters")
        String name,

        @NotNull(message = "Account type is required")
        AccountType type,

        NormalSide normalSide,
        CashFlowCategory cashFlowCategory,
        boolean cashEquivalent,
        boolean contra,

        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        String currency,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description
) {
}