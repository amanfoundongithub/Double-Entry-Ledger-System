package com.ledger_core.ledger_core.accounts.domain;

public enum AccountType {

    ASSET(NormalSide.DEBIT),
    LIABILITY(NormalSide.CREDIT),
    EQUITY(NormalSide.CREDIT),
    REVENUE(NormalSide.CREDIT),
    EXPENSE(NormalSide.DEBIT);

    private final NormalSide defaultNormalSide;

    AccountType(NormalSide defaultNormalSide) {
        this.defaultNormalSide = defaultNormalSide;
    }

    public NormalSide defaultNormalSide() {
        return defaultNormalSide;
    }
    
}