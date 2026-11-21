package com.ledger_core.ledger_core.templates.api;

import com.ledger_core.ledger_core.templates.application.JournalEntryTemplateService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
public class JournalEntryTemplateController {

    private final JournalEntryTemplateService templateService;

    public JournalEntryTemplateController(JournalEntryTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable String id) {
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody CreateTemplateRequest req) {
        TemplateResponse created = templateService.createCustomTemplate(req);
        return ResponseEntity
            .created(URI.create("/api/v1/templates/" + created.id()))
            .body(created);
    }
}