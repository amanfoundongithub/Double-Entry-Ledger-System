package com.ledger_core.ledger_core.periods.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreatePeriodRequest(
        @NotBlank(message = "Period id is required (e.g., 2026-11)")
        @Size(max = 20)
        String id,

        @NotBlank(message = "Period name is required")
        @Size(max = 100)
        String name,

        @NotNull(message = "startDate is required")
        Instant startDate,

        @NotNull(message = "endDate is required")
        Instant endDate
) {
}