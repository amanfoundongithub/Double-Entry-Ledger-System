package com.ledger_core.ledger_core.templates.domain;

import java.time.Instant;
import java.time.LocalDate;
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
    TemplateSchedule schedule,
    Instant createdAt,
    Instant updatedAt
) {
    public record TemplateSchedule(
        ScheduleFrequency frequency,
        Integer interval,
        LocalDate nextRunDate,
        Boolean autoAdvanceOnPost
    ) {
        public enum ScheduleFrequency {
            NONE,
            WEEKLY,
            MONTHLY,
            QUARTERLY,
            YEARLY
        }

        public static TemplateSchedule unscheduled() {
            return new TemplateSchedule(ScheduleFrequency.NONE, 1, null, false);
        }

        public ScheduleFrequency resolvedFrequency() {
            return frequency != null ? frequency : ScheduleFrequency.NONE;
        }

        public int resolvedInterval() {
            return (interval != null && interval > 0) ? interval : 1;
        }

        public boolean resolvedAutoAdvance() {
            return autoAdvanceOnPost == null || autoAdvanceOnPost;
        }

        public LocalDate advanceNextRunDate() {
            if (nextRunDate == null || resolvedFrequency() == ScheduleFrequency.NONE) {
                return nextRunDate;
            }
            int step = resolvedInterval();
            return switch (resolvedFrequency()) {
                case NONE -> nextRunDate;
                case WEEKLY -> nextRunDate.plusWeeks(step);
                case MONTHLY -> nextRunDate.plusMonths(step);
                case QUARTERLY -> nextRunDate.plusMonths(step * 3L);
                case YEARLY -> nextRunDate.plusYears(step);
            };
        }
    }

    public TemplateSchedule resolvedSchedule() {
        return schedule != null ? schedule : TemplateSchedule.unscheduled();
    }

    public JournalEntryTemplate withSchedule(TemplateSchedule nextSchedule, Instant now) {
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
            nextSchedule != null ? nextSchedule : TemplateSchedule.unscheduled(),
            createdAt,
            now
        );
    }
}