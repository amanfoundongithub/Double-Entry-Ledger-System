package com.ledger_core.ledger_core.templates.api;

import com.ledger_core.ledger_core.templates.api.CreateTemplateRequest.TemplateScheduleDto;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate.TemplateSchedule;
import com.ledger_core.ledger_core.templates.domain.TemplateCategory;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import java.time.Instant;
import java.util.List;

public record TemplateResponse(
    String id,
    String code,
    String name,
    String description,
    String defaultDescription,
    String referencePrefix,
    TemplateCategory category,
    boolean systemPreset,
    List<TemplateLineDef> lines,
    TemplateScheduleDto schedule,
    Instant createdAt
) {
    public static TemplateResponse fromDomain(JournalEntryTemplate t) {
        TemplateSchedule s = t.resolvedSchedule();
        TemplateScheduleDto scheduleDto = new TemplateScheduleDto(
            s.resolvedFrequency(),
            s.resolvedInterval(),
            s.nextRunDate(),
            s.resolvedAutoAdvance()
        );

        return new TemplateResponse(
            t.id(),
            t.code(),
            t.name(),
            t.description(),
            t.defaultDescription(),
            t.referencePrefix(),
            t.category(),
            t.systemPreset(),
            t.lines(),
            scheduleDto,
            t.createdAt()
        );
    }
}