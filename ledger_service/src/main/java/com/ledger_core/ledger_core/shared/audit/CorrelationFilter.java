package com.ledger_core.ledger_core.shared.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    private final CorrelationContext correlationContext;

    public CorrelationFilter(CorrelationContext correlationContext) {
        this.correlationContext = correlationContext;
    }

    @Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
) throws ServletException, IOException {
    try {
        String correlationId = correlationContext.getOrCreate(
                request.getHeader(CorrelationContext.HEADER_NAME)
        );
        response.setHeader(CorrelationContext.HEADER_NAME, correlationId);
        filterChain.doFilter(request, response);
    } finally {
        correlationContext.clear();
    }
}
}