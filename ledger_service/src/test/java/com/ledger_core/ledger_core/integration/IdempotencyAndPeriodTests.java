package com.ledger_core.ledger_core.integration;

import com.ledger_core.ledger_core.idempotency.application.IdempotencyService;
import com.ledger_core.ledger_core.idempotency.domain.IdempotencyRecord;
import com.ledger_core.ledger_core.idempotency.infrastructure.IdempotencyRepository;
import com.ledger_core.ledger_core.periods.application.PeriodEnforcementService;
import com.ledger_core.ledger_core.periods.domain.AccountingPeriod;
import com.ledger_core.ledger_core.periods.domain.PeriodStatus;
import com.ledger_core.ledger_core.periods.infrastructure.AccountingPeriodRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.PeriodClosedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAndPeriodTests {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private AccountingPeriodRepository periodRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-20T17:15:00Z"), ZoneOffset.UTC);

    private IdempotencyService idempotencyService;
    private PeriodEnforcementService periodEnforcementService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository, fixedClock);
        periodEnforcementService = new PeriodEnforcementService(periodRepository, fixedClock);
    }

    @Test
    @DisplayName("Idempotency replays stored response when key and payload hash match")
    void replaysResponseOnMatchingPayload() {
        String key = "idem-key-123";
        String payload = "{\"description\":\"Test sale\"}";
        String hash = idempotencyService.hashPayload(payload);

        IdempotencyRecord stored = new IdempotencyRecord(
                "rec-1", key, hash, 201, "{\"entryNumber\":\"JE-2026-000001\"}", fixedClock.instant()
        );
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(stored));

        Optional<IdempotencyRecord> result = idempotencyService.checkExisting(key, hash);
        assertTrue(result.isPresent());
        assertEquals(201, result.get().responseStatusCode());
        assertEquals("{\"entryNumber\":\"JE-2026-000001\"}", result.get().responsePayload());
    }

    @Test
    @DisplayName("Idempotency throws DomainException when key is reused with different payload")
    void rejectsMismatchedPayloadForSameKey() {
        String key = "idem-key-123";
        String originalHash = idempotencyService.hashPayload("{\"amount\":100}");
        String differentHash = idempotencyService.hashPayload("{\"amount\":200}");

        IdempotencyRecord stored = new IdempotencyRecord(
                "rec-1", key, originalHash, 201, "{}", fixedClock.instant()
        );
        when(idempotencyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(stored));

        assertThrows(DomainException.class, () -> idempotencyService.checkExisting(key, differentHash));
    }

    @Test
    @DisplayName("Period enforcement allows posting into OPEN period within date bounds")
    void allowsPostingInOpenPeriod() {
        AccountingPeriod openPeriod = AccountingPeriod.open(
                "2026-11",
                "November 2026",
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-30T23:59:59.999Z")
        );
        when(periodRepository.findById("2026-11")).thenReturn(Optional.of(openPeriod));

        assertDoesNotThrow(() ->
                periodEnforcementService.assertPostingAllowed("2026-11", Instant.parse("2026-11-20T17:15:00Z"))
        );
    }

    @Test
    @DisplayName("Period enforcement blocks posting into CLOSED or LOCKED period")
    void blocksPostingInClosedOrLockedPeriod() {
        AccountingPeriod closedPeriod = new AccountingPeriod(
                "2026-10",
                "October 2026",
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-31T23:59:59.999Z"),
                PeriodStatus.CLOSED,
                Instant.parse("2026-11-02T10:00:00Z"),
                "cfo"
        );
        when(periodRepository.findById("2026-10")).thenReturn(Optional.of(closedPeriod));

        assertThrows(PeriodClosedException.class, () ->
                periodEnforcementService.assertPostingAllowed("2026-10", Instant.parse("2026-10-15T12:00:00Z"))
        );
        verify(periodRepository, never()).save(any());
    }
}