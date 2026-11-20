package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BalanceSheetResponse(
        Instant asOfDate,
        List<StatementLine> assets,
        List<StatementLine> liabilities,
        List<StatementLine> equity,
        BigDecimal cumulativeUnclosedNetIncome,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        boolean balanced
) {
}