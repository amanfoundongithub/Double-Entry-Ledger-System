package com.ledger_core.ledger_core.periods.api;

import com.ledger_core.ledger_core.periods.application.PeriodEnforcementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/periods")
public class AccountingPeriodController {

    private final PeriodEnforcementService periodService;

    public AccountingPeriodController(PeriodEnforcementService periodService) {
        this.periodService = periodService;
    }

    @PostMapping
    public ResponseEntity<PeriodResponse> createPeriod(@Valid @RequestBody CreatePeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodService.createPeriod(request));
    }

    @GetMapping
    public ResponseEntity<List<PeriodResponse>> listPeriods() {
        return ResponseEntity.ok(periodService.listPeriods());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PeriodResponse> getPeriod(@PathVariable String id) {
        return ResponseEntity.ok(periodService.getPeriod(id));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<PeriodResponse> closePeriod(
            @PathVariable String id,
            @RequestParam(required = false) String closedBy
    ) {
        return ResponseEntity.ok(periodService.closePeriod(id, closedBy));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<PeriodResponse> lockPeriod(
            @PathVariable String id,
            @RequestParam(required = false) String closedBy
    ) {
        return ResponseEntity.ok(periodService.lockPeriod(id, closedBy));
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<PeriodResponse> reopenPeriod(@PathVariable String id) {
        return ResponseEntity.ok(periodService.reopenPeriod(id));
    }
}