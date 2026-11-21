package com.ledger_core.ledger_core.templates.application;

import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.templates.api.CreateTemplateRequest;
import com.ledger_core.ledger_core.templates.api.TemplateResponse;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate.TemplateSchedule;
import com.ledger_core.ledger_core.templates.domain.JournalEntryTemplate.TemplateSchedule.ScheduleFrequency;
import com.ledger_core.ledger_core.templates.domain.TemplateAllocationType;
import com.ledger_core.ledger_core.templates.domain.TemplateLineDef;
import com.ledger_core.ledger_core.templates.infrastructure.JournalEntryTemplateDocument;
import com.ledger_core.ledger_core.templates.infrastructure.JournalEntryTemplateRepository;
import java.time.Instant;
import java.time.LocalDate;
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

    @Transactional
    public TemplateResponse advanceSchedule(String id) {
        JournalEntryTemplate template = templateRepo.findById(id)
                .map(JournalEntryTemplateDocument::toDomain)
                .orElseThrow(() -> new DomainException("Template not found: " + id));

        TemplateSchedule current = template.resolvedSchedule();
        if (current.resolvedFrequency() != ScheduleFrequency.NONE && current.resolvedAutoAdvance()) {
            LocalDate advancedDate = current.advanceNextRunDate();
            TemplateSchedule updatedSchedule = new TemplateSchedule(
                    current.resolvedFrequency(),
                    current.resolvedInterval(),
                    advancedDate,
                    current.resolvedAutoAdvance()
            );
            JournalEntryTemplate updated = templateRepo
                    .save(JournalEntryTemplateDocument.fromDomain(
                            template.withSchedule(updatedSchedule, Instant.now())))
                    .toDomain();
            return TemplateResponse.fromDomain(updated);
        }

        return TemplateResponse.fromDomain(template);
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

        TemplateSchedule schedule = resolveScheduleFromRequest(req);
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
                schedule,
                now,
                now);

        JournalEntryTemplate saved = templateRepo
                .save(JournalEntryTemplateDocument.fromDomain(domainTemplate))
                .toDomain();
        return TemplateResponse.fromDomain(saved);
    }

    private TemplateSchedule resolveScheduleFromRequest(CreateTemplateRequest req) {
        // Matches TemplateScheduleDef on CreateTemplateRequest:
        // frequency, interval, nextRunDate, autoAdvanceOnPost
        if (req.schedule() == null) {
            return TemplateSchedule.unscheduled();
        }
        ScheduleFrequency freq = req.schedule().frequency() != null
                ? req.schedule().frequency()
                : ScheduleFrequency.NONE;
        if (freq == ScheduleFrequency.NONE) {
            return TemplateSchedule.unscheduled();
        }
        int interval = (req.schedule().interval() != null && req.schedule().interval() > 0)
                ? req.schedule().interval()
                : 1;
        LocalDate nextRun = req.schedule().nextRunDate();
        if (nextRun == null) {
            throw new DomainException("nextRunDate is required when schedule frequency is " + freq);
        }
        boolean autoAdvance = req.schedule().autoAdvanceOnPost() == null
                || req.schedule().autoAdvanceOnPost();
        return new TemplateSchedule(freq, interval, nextRun, autoAdvance);
    }
}