package com.ledger_core.ledger_core.journal.api;

import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PostJournalLineRequest(
        String accountId,
        String accountCode,

        @DecimalMin(value = "0.00", message = "debitAmount must be non-negative")
        BigDecimal debitAmount,

        @DecimalMin(value = "0.00", message = "creditAmount must be non-negative")
        BigDecimal creditAmount,

        @Size(max = 250, message = "memo must be at most 250 characters")
        String memo,

        CashFlowCategory cashFlowCategory
) {
}