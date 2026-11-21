package com.ledger_core.ledger_core.templates.infrastructure;

import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate.TemplateSchedule;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate.TemplateSchedule.ScheduleFrequency;
import com.ledger_core.ledger_core.templates.domain.TemplateCategory;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "journal_entry_templates")
public record JournalEntryTemplateDocument(
    @Id String id,
    @Indexed(unique = true) String code,
    String name,
    String description,
    String defaultDescription,
    String referencePrefix,
    TemplateCategory category,
    boolean systemPreset,
    List<TemplateLineDef> lines,
    TemplateScheduleDocument schedule,
    Instant createdAt,
    Instant updatedAt
) {
    public record TemplateScheduleDocument(
        ScheduleFrequency frequency,
        Integer interval,
        LocalDate nextRunDate,
        Boolean autoAdvanceOnPost
    ) {
        public static TemplateScheduleDocument fromDomain(TemplateSchedule s) {
            if (s == null) {
                return null;
            }
            return new TemplateScheduleDocument(
                s.resolvedFrequency(),
                s.resolvedInterval(),
                s.nextRunDate(),
                s.resolvedAutoAdvance()
            );
        }

        public TemplateSchedule toDomain() {
            return new TemplateSchedule(
                frequency != null ? frequency : ScheduleFrequency.NONE,
                interval,
                nextRunDate,
                autoAdvanceOnPost
            );
        }
    }

    public static JournalEntryTemplateDocument fromDomain(JournalEntryTemplate t) {
        return new JournalEntryTemplateDocument(
            t.id(),
            t.code(),
            t.name(),
            t.description(),
            t.defaultDescription(),
            t.referencePrefix(),
            t.category(),
            t.systemPreset(),
            t.lines(),
            TemplateScheduleDocument.fromDomain(t.resolvedSchedule()),
            t.createdAt(),
            t.updatedAt()
        );
    }

    public JournalEntryTemplate toDomain() {
        return new JournalEntryTemplate(
            id,
            code,
            name,
            description,
            defaultDescription,
            referencePrefix,
            category,
            systemPreset,
            lines,
            schedule != null ? schedule.toDomain() : TemplateSchedule.unscheduled(),
            createdAt,
            updatedAt
        );
    }
}