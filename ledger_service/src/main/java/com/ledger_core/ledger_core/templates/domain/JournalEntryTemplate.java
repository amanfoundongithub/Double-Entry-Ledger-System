package com.ledger_core.ledger_core.templates.domain;

import java.time.Instant;
import java.util.List;

public record JournalEntryTemplate(
    String id,
    String code,
    String name,
    String description,
    String defaultDescription,
    String referencePrefix,
    TemplateCategory category,
    boolean systemPreset,
    List<TemplateLineDef> lines,
    Instant createdAt,
    Instant updatedAt
) {}