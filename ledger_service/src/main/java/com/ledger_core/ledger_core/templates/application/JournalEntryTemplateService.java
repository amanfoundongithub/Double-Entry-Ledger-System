package com.ledger_core.ledger_core.templates.application;

import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.templates.api.CreateTemplateRequest;
import com.ledger_core.ledger_core.templates.api.TemplateResponse;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate;
import com.ledger_core.ledger_core.templates.domain.TemplateAllocationType;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import com.ledger_core.ledger_core.templates.infrastructure.JournalEntryTemplateDocument;
import com.ledger_core.ledger_core.templates.infrastructure.JournalEntryTemplateRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalEntryTemplateService {

    private final JournalEntryTemplateRepository templateRepo;
    private final AccountRepository accountRepo;

    public JournalEntryTemplateService(
            JournalEntryTemplateRepository templateRepo,
            AccountRepository accountRepo) {
        this.templateRepo = templateRepo;
        this.accountRepo = accountRepo;
    }

    public List<TemplateResponse> getAllTemplates() {
        return templateRepo.findAll().stream()
                .map(JournalEntryTemplateDocument::toDomain)
                .map(TemplateResponse::fromDomain)
                .toList();
    }

    public TemplateResponse getTemplateById(String id) {
        JournalEntryTemplate template = templateRepo.findById(id)
                .map(JournalEntryTemplateDocument::toDomain)
                .orElseThrow(() -> new DomainException("Template not found: " + id));
        return TemplateResponse.fromDomain(template);
    }

    @Transactional
    public void deleteTemplate(String id) {
        if (!templateRepo.existsById(id)) {
            throw new DomainException("Template not found with id: " + id);
        }
        templateRepo.deleteById(id);
    }

    public TemplateResponse createCustomTemplate(CreateTemplateRequest req) {
        if (templateRepo.existsByCode(req.code())) {
            throw new DomainException("Template code already exists: " + req.code());
        }

        boolean hasDebit = req.lines().stream().anyMatch(l -> l.side() == NormalSide.DEBIT);
        boolean hasCredit = req.lines().stream().anyMatch(l -> l.side() == NormalSide.CREDIT);
        if (!hasDebit || !hasCredit) {
            throw new DomainException("Template must contain at least one DEBIT line and one CREDIT line.");
        }

        for (TemplateLineDef line : req.lines()) {
            if (!accountRepo.existsByCode(line.accountCode())) {
                throw new DomainException("Referenced account code does not exist: " + line.accountCode());
            }
            TemplateAllocationType type = line.effectiveAmountType();
            if (type == TemplateAllocationType.PERCENT) {
                if (line.allocationPercent() == null || line.allocationPercent().signum() <= 0) {
                    throw new DomainException(
                            "PERCENT allocation requires a positive allocationPercent for account: "
                                    + line.accountCode());
                }
            }
        }

        Instant now = Instant.now();
        JournalEntryTemplate domainTemplate = new JournalEntryTemplate(
                UUID.randomUUID().toString(),
                req.code().toUpperCase().trim(),
                req.name().trim(),
                req.description(),
                req.defaultDescription(),
                req.referencePrefix() != null ? req.referencePrefix() : "JE-",
                req.category(),
                false,
                req.lines(),
                now,
                now);

        JournalEntryTemplate saved = templateRepo
                .save(JournalEntryTemplateDocument.fromDomain(domainTemplate))
                .toDomain();
        return TemplateResponse.fromDomain(saved);
    }
}