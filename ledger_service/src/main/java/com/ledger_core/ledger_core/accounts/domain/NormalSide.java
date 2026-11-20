package com.ledger_core.ledger_core.accounts.domain;

public enum NormalSide {

    DEBIT,
    CREDIT;

    public NormalSide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
    
}