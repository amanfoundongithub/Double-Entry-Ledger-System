package com.ledger_core.ledger_core.journal.api;

import com.ledger_core.ledger_core.idempotency.application.IdempotencyService;
import com.ledger_core.ledger_core.journal.application.ReversalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

record ReversalRequestBody(String entryId, String reason, String createdBy) {
}

@RestController
@RequestMapping("/api/v1/journal-entries")
public class ReversalController {

    private final ReversalService reversalService;
    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;

    public ReversalController(
            ReversalService reversalService,
            IdempotencyService idempotencyService,
            JsonMapper jsonMapper
    ) {
        this.reversalService = reversalService;
        this.idempotencyService = idempotencyService;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<String> reverseEntry(
            @PathVariable("id") String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String createdBy
    ) throws JacksonException {
        ReversalRequestBody canonicalBody = new ReversalRequestBody(id, reason, createdBy);
        String rawRequestJson = jsonMapper.writeValueAsString(canonicalBody);
        String requestHash = idempotencyService.hashPayload(rawRequestJson);

        var existing = idempotencyService.checkExisting(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return ResponseEntity.status(existing.get().responseStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(existing.get().responsePayload());
        }

        JournalEntryResponse reversal = reversalService.reverseEntry(id, reason, idempotencyKey, createdBy);
        String responseJson = jsonMapper.writeValueAsString(reversal);
        idempotencyService.recordResponse(idempotencyKey, requestHash, HttpStatus.CREATED.value(), responseJson);

        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseJson);
    }
}