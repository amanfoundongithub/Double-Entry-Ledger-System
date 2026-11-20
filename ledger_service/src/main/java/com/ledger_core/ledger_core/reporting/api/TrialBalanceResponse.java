package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TrialBalanceResponse(
        Instant asOfDate,
        List<TrialBalanceRow> rows,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean balanced
) {
}