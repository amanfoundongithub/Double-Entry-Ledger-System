package com.ledger_core.ledger_core.templates.api;

import com.ledger_core.ledger_core.templates.domain.TemplateCategory;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTemplateRequest(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    @NotBlank String defaultDescription,
    String referencePrefix,
    @NotNull TemplateCategory category,
    @NotEmpty List<TemplateLineDef> lines
) {}