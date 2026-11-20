package com.ledger_core.ledger_core.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CashFlowStatementResponse(
        Instant startDate,
        Instant endDate,
        List<CashFlowLine> operatingActivities,
        List<CashFlowLine> investingActivities,
        List<CashFlowLine> financingActivities,
        BigDecimal operatingCashFlow,
        BigDecimal investingCashFlow,
        BigDecimal financingCashFlow,
        BigDecimal netCashChange,
        BigDecimal beginningCashBalance,
        BigDecimal endingCashBalance,
        boolean reconciled
) {
}