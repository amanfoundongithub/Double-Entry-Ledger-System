package com.ledger_core.ledger_core.reporting.api;

import com.ledger_core.ledger_core.reporting.application.StatementEngineService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final StatementEngineService statementEngineService;

    public ReportingController(StatementEngineService statementEngineService) {
        this.statementEngineService = statementEngineService;
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceResponse> getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOfDate
    ) {
        return ResponseEntity.ok(statementEngineService.getTrialBalance(asOfDate));
    }

    @GetMapping("/income-statement")
    public ResponseEntity<IncomeStatementResponse> getIncomeStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(statementEngineService.getIncomeStatement(startDate, endDate));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOfDate
    ) {
        return ResponseEntity.ok(statementEngineService.getBalanceSheet(asOfDate));
    }

    @GetMapping("/cash-flow")
public ResponseEntity<CashFlowStatementResponse> getCashFlowStatement(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
) {
    return ResponseEntity.ok(statementEngineService.getCashFlowStatement(startDate, endDate));
}
}