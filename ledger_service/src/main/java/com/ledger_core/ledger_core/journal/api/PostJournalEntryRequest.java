package com.ledger_core.ledger_core.journal.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record PostJournalEntryRequest(
        @Size(max = 100) String reference,
        @Size(max = 60) String sourceType,
        Instant postingDate,
        Instant transactionDate,

        @NotBlank(message = "description is required")
        @Size(max = 500)
        String description,

        @Size(max = 20) String periodId,

        @NotEmpty(message = "At least two journal lines are required")
        @Size(min = 2, message = "At least two journal lines are required")
        List<@Valid PostJournalLineRequest> lines,

        @Size(max = 100) String createdBy
) {
}