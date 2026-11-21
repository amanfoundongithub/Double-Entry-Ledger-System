package com.ledger_core.ledger_core.templates.infrastructure;

import com.ledger_core.ledger_core.templates.domain.TemplateCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JournalEntryTemplateRepository extends MongoRepository<JournalEntryTemplateDocument, String> {
    Optional<JournalEntryTemplateDocument> findByCode(String code);
    List<JournalEntryTemplateDocument> findByCategory(TemplateCategory category);
    boolean existsByCode(String code);
}