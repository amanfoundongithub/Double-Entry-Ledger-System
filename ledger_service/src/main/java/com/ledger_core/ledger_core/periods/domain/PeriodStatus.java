package com.ledger_core.ledger_core.periods.domain;

public enum PeriodStatus {
    OPEN,
    CLOSED,
    LOCKED;

    public boolean allowsPosting() {
        return this == OPEN;
    }
}