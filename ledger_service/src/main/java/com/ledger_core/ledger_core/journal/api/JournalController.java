package com.ledger_core.ledger_core.journal.api;

import com.ledger_core.ledger_core.idempotency.application.IdempotencyService;
import com.ledger_core.ledger_core.journal.application.JournalPostingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalController {

    private final JournalPostingService postingService;
    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;

    public JournalController(
            JournalPostingService postingService,
            IdempotencyService idempotencyService,
            JsonMapper jsonMapper
    ) {
        this.postingService = postingService;
        this.idempotencyService = idempotencyService;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping
    public ResponseEntity<String> postEntry(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PostJournalEntryRequest request
    ) throws JacksonException {
        String rawRequestJson = jsonMapper.writeValueAsString(request);
        String requestHash = idempotencyService.hashPayload(rawRequestJson);

        var existing = idempotencyService.checkExisting(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return ResponseEntity.status(existing.get().responseStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(existing.get().responsePayload());
        }

        JournalEntryResponse created = postingService.postEntry(request, idempotencyKey);
        String responseJson = jsonMapper.writeValueAsString(created);
        idempotencyService.recordResponse(idempotencyKey, requestHash, HttpStatus.CREATED.value(), responseJson);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseJson);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(postingService.getById(id));
    }

    @GetMapping("/number/{entryNumber}")
    public ResponseEntity<JournalEntryResponse> getByEntryNumber(@PathVariable String entryNumber) {
        return ResponseEntity.ok(postingService.getByEntryNumber(entryNumber));
    }

    @GetMapping
    public ResponseEntity<List<JournalEntryResponse>> listByAccount(@RequestParam String accountId) {
        return ResponseEntity.ok(postingService.listByAccount(accountId));
    }

    @GetMapping("/recent")
public ResponseEntity<List<JournalEntryResponse>> listRecent(
        @RequestParam(defaultValue = "25") int limit
) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    return ResponseEntity.ok(postingService.listRecent(safeLimit));
}
}