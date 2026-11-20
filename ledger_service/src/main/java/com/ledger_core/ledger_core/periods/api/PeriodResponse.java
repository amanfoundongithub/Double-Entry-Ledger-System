package com.ledger_core.ledger_core.periods.api;

import com.ledger_core.ledger_core.periods.domain.AccountingPeriod;
import com.ledger_core.ledger_core.periods.domain.PeriodStatus;

import java.time.Instant;

public record PeriodResponse(
        String id,
        String name,
        Instant startDate,
        Instant endDate,
        PeriodStatus status,
        Instant closedAt,
        String closedBy
) {
    public static PeriodResponse from(AccountingPeriod period) {
        return new PeriodResponse(
                period.getId(),
                period.getName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                period.getClosedAt(),
                period.getClosedBy()
        );
    }
}