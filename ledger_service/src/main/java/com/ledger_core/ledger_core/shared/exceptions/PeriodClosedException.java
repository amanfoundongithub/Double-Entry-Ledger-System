package com.ledger_core.ledger_core.shared.exceptions;

public class PeriodClosedException extends DomainException {

    public PeriodClosedException(String periodId, String status) {
        super("Cannot post to period '" + periodId + "' with status '" + status + "'");
    }
    
}