package com.ledger_core.ledger_core.templates.infrastructure;

import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate;
import com.ledger_core.ledger_core.templates.domain.TemplateCategory;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import java.time.Instant;
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
    Instant createdAt,
    Instant updatedAt
) {
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
            createdAt,
            updatedAt
        );
    }
}