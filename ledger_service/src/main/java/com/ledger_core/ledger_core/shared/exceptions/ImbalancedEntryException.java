package com.ledger_core.ledger_core.shared.exceptions;

import java.math.BigDecimal;

public class ImbalancedEntryException extends DomainException {
    
    public ImbalancedEntryException(BigDecimal totalDebits, BigDecimal totalCredits) {
        super("Journal entry is imbalanced: totalDebits=" + totalDebits + ", totalCredits=" + totalCredits);
    }

}