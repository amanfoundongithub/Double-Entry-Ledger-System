package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IncomeStatementResponse(
        Instant startDate,
        Instant endDate,
        List<StatementLine> revenues,
        List<StatementLine> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome
) {
}