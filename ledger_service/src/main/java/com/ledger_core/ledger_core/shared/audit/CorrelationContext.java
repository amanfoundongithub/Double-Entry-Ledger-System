package com.ledger_core.ledger_core.shared.audit;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

@Component
@RequestScope
public class CorrelationContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private String correlationId = UUID.randomUUID().toString();

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            this.correlationId = correlationId.trim();
        }
        MDC.put(MDC_KEY, this.correlationId);
    }

    public void clearMdc() {
        MDC.remove(MDC_KEY);
    }
}