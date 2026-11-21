package com.ledger_core.ledger_core.templates.domain;

import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import java.math.BigDecimal;

public record TemplateLineDef(
    String accountCode,
    String accountName,
    NormalSide side,
    CashFlowCategory cashFlowCategory,
    String defaultMemo,
    BigDecimal defaultAmount,
    boolean balancingLeg
) {}