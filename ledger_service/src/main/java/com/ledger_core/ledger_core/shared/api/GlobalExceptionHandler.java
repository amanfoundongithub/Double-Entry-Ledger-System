package com.ledger_core.ledger_core.shared.api;

import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.ImbalancedEntryException;
import com.ledger_core.ledger_core.shared.exceptions.PeriodClosedException;
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        problem.setTitle("Imbalanced Journal Entry");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(PeriodClosedException.class)
    public ProblemDetail handlePeriodClosed(PeriodClosedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Accounting Period Not Open");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({DomainException.class, IllegalArgumentException.class})
    public ProblemDetail handleDomainException(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Ledger Domain Violation");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Duplicate unique key or concurrent idempotency conflict"
        );
        problem.setTitle("Conflict");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}