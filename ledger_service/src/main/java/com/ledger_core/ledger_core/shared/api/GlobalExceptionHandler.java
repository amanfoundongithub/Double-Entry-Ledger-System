package com.ledger_core.ledger_core.shared.api;

import com.ledger_core.ledger_core.shared.audit.CorrelationContext;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.ImbalancedEntryException;
import com.ledger_core.ledger_core.shared.exceptions.PeriodClosedException;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ImbalancedEntryException.class)
    public ProblemDetail handleImbalancedEntry(ImbalancedEntryException ex) {
        return enrich(
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage()),
                "Imbalanced Journal Entry"
        );
    }

    @ExceptionHandler(PeriodClosedException.class)
    public ProblemDetail handlePeriodClosed(PeriodClosedException ex) {
        return enrich(
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()),
                "Accounting Period Not Open"
        );
    }

    @ExceptionHandler({DomainException.class, IllegalArgumentException.class})
    public ProblemDetail handleDomainException(RuntimeException ex) {
        return enrich(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()),
                "Ledger Domain Violation"
        );
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        return enrich(
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "Duplicate unique key or concurrent idempotency conflict"
                ),
                "Conflict"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return enrich(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details),
                "Validation Failed"
        );
    }

    private ProblemDetail enrich(ProblemDetail problem, String title) {
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        String correlationId = MDC.get(CorrelationContext.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}