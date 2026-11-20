package com.ledger_core.ledger_core.periods.application;

import com.ledger_core.ledger_core.periods.api.CreatePeriodRequest;
import com.ledger_core.ledger_core.periods.api.PeriodResponse;
import com.ledger_core.ledger_core.periods.domain.AccountingPeriod;
import com.ledger_core.ledger_core.periods.infrastructure.AccountingPeriodRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.PeriodClosedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PeriodEnforcementService {

    private final AccountingPeriodRepository periodRepository;
    private final Clock clock;

    public PeriodEnforcementService(AccountingPeriodRepository periodRepository, Clock clock) {
        this.periodRepository = periodRepository;
        this.clock = clock;
    }

    /**
     * Validates that the target period exists, contains the postingDate, and is OPEN.
     * Throws PeriodClosedException if CLOSED or LOCKED.
     */
    @Transactional(readOnly = true)
    public void assertPostingAllowed(String periodId, Instant postingDate) {
        AccountingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new DomainException("Accounting period '" + periodId + "' does not exist"));

        if (!period.contains(postingDate)) {
            throw new DomainException(
                    "Posting date " + postingDate + " falls outside period '" + period.getId()
                            + "' (" + period.getStartDate() + " to " + period.getEndDate() + ")"
            );
        }

        if (!period.getStatus().allowsPosting()) {
            throw new PeriodClosedException(period.getId(), period.getStatus().name());
        }
    }

    @Transactional
    public PeriodResponse createPeriod(CreatePeriodRequest request) {
        String id = request.id().trim();
        if (periodRepository.existsById(id)) {
            throw new DomainException("Accounting period '" + id + "' already exists");
        }

        AccountingPeriod period = AccountingPeriod.open(
                id,
                request.name(),
                request.startDate(),
                request.endDate()
        );
        return PeriodResponse.from(periodRepository.save(period));
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> listPeriods() {
        return periodRepository.findAllByOrderByStartDateAsc().stream()
                .map(PeriodResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PeriodResponse getPeriod(String id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new DomainException("Accounting period not found: " + id));
        return PeriodResponse.from(period);
    }

    @Transactional
    public PeriodResponse closePeriod(String id, String closedBy) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new DomainException("Accounting period not found: " + id));
        period.close(closedBy, Instant.now(clock));
        return PeriodResponse.from(periodRepository.save(period));
    }

    @Transactional
    public PeriodResponse lockPeriod(String id, String closedBy) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new DomainException("Accounting period not found: " + id));
        period.lock(closedBy, Instant.now(clock));
        return PeriodResponse.from(periodRepository.save(period));
    }

    @Transactional
    public PeriodResponse reopenPeriod(String id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new DomainException("Accounting period not found: " + id));
        period.reopen();
        return PeriodResponse.from(periodRepository.save(period));
    }
}