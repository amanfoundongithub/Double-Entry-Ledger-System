package com.ledger_core.ledger_core.periods.domain;

import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "accounting_periods")
public class AccountingPeriod {

    @Id
    private String id; // e.g., "2026-11"

    private String name;
    private Instant startDate;
    private Instant endDate;
    private PeriodStatus status;
    private Instant closedAt;
    private String closedBy;

    protected AccountingPeriod() {
    }

    public AccountingPeriod(
            String id,
            String name,
            Instant startDate,
            Instant endDate,
            PeriodStatus status,
            Instant closedAt,
            String closedBy
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("period id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("period name must not be blank");
        }
        Objects.requireNonNull(startDate, "startDate is required");
        Objects.requireNonNull(endDate, "endDate is required");
        if (endDate.isBefore(startDate)) {
            throw new DomainException("AccountingPeriod endDate must not be before startDate");
        }
        Objects.requireNonNull(status, "status is required");

        this.id = id.trim();
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    public static AccountingPeriod open(String id, String name, Instant startDate, Instant endDate) {
        return new AccountingPeriod(id, name, startDate, endDate, PeriodStatus.OPEN, null, null);
    }

    public void close(String closedBy, Instant now) {
        if (this.status == PeriodStatus.LOCKED) {
            throw new DomainException("Period '" + this.id + "' is LOCKED and cannot be modified");
        }
        this.status = PeriodStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(now, "closedAt is required");
        this.closedBy = closedBy != null && !closedBy.isBlank() ? closedBy.trim() : "system";
    }

    public void lock(String closedBy, Instant now) {
        this.status = PeriodStatus.LOCKED;
        if (this.closedAt == null) {
            this.closedAt = Objects.requireNonNull(now, "closedAt is required");
        }
        if (this.closedBy == null && closedBy != null && !closedBy.isBlank()) {
            this.closedBy = closedBy.trim();
        }
    }

    public void reopen() {
        if (this.status == PeriodStatus.LOCKED) {
            throw new DomainException("Period '" + this.id + "' is LOCKED and cannot be reopened");
        }
        this.status = PeriodStatus.OPEN;
        this.closedAt = null;
        this.closedBy = null;
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startDate) && !instant.isAfter(endDate);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public PeriodStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }
}