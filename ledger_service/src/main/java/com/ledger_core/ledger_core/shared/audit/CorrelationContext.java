package com.ledger_core.ledger_core.shared.audit;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CorrelationContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private final ThreadLocal<String> currentCorrelationId = new ThreadLocal<>();

    public String getOrCreate(String incomingHeader) {
        String value = (incomingHeader != null && !incomingHeader.isBlank())
                ? incomingHeader.trim()
                : UUID.randomUUID().toString();
        setCorrelationId(value);
        return value;
    }

    public void setCorrelationId(String correlationId) {
        currentCorrelationId.set(correlationId);
        MDC.put(MDC_KEY, correlationId);
    }

    public String getCorrelationId() {
        String id = currentCorrelationId.get();
        if (id == null) {
            id = MDC.get(MDC_KEY);
        }
        return id;
    }

    public void clear() {
        currentCorrelationId.remove();
        MDC.remove(MDC_KEY);
    }
}