package com.ledger_core.ledger_core.journal.domain;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.exceptions.ImbalancedEntryException;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JournalInvariantsTest {

    private final Instant now = Instant.parse("2026-11-20T17:15:00Z");

    @Test
    @DisplayName("JournalLine rejects negative amounts")
    void journalLineRejectsNegativeAmounts() {
        assertThrows(DomainException.class, () -> new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("-10.00"), MonetaryAmount.ZERO, "memo", CashFlowCategory.OPERATING
        ));

        assertThrows(DomainException.class, () -> new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.ZERO, MonetaryAmount.of("-10.00"), "memo", CashFlowCategory.OPERATING
        ));
    }

    @Test
    @DisplayName("JournalLine rejects both debit and credit positive on the same line")
    void journalLineRejectsBothSidesPositive() {
        assertThrows(DomainException.class, () -> new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("10.00"), MonetaryAmount.of("10.00"), "memo", CashFlowCategory.OPERATING
        ));
    }

    @Test
    @DisplayName("JournalLine rejects both debit and credit zero")
    void journalLineRejectsBothSidesZero() {
        assertThrows(DomainException.class, () -> new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO, "memo", CashFlowCategory.OPERATING
        ));
    }

    @Test
    @DisplayName("JournalEntry rejects fewer than 2 lines")
    void journalEntryRejectsFewerThanTwoLines() {
        JournalLine line = new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("100.00"), MonetaryAmount.ZERO, "memo", CashFlowCategory.OPERATING
        );

        assertThrows(DomainException.class, () -> new JournalEntry(
                "je-1", "JE-2026-000001", "REF-1", "STANDARD", EntryStatus.POSTED,
                now, now, "Test entry", "2026-11", null, null, "idem-1",
                List.of(line), MonetaryAmount.ZERO, now, "admin"
        ));
    }

    @Test
    @DisplayName("JournalEntry rejects imbalanced debits and credits")
    void journalEntryRejectsImbalance() {
        JournalLine debit = new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("100.00"), MonetaryAmount.ZERO, "debit leg", CashFlowCategory.OPERATING
        );
        JournalLine credit = new JournalLine(
                "acc-2", "4010", "Revenue", AccountType.REVENUE,
                MonetaryAmount.ZERO, MonetaryAmount.of("99.99"), "short credit leg", CashFlowCategory.NONE
        );

        ImbalancedEntryException ex = assertThrows(ImbalancedEntryException.class, () -> new JournalEntry(
                "je-1", "JE-2026-000001", "REF-1", "STANDARD", EntryStatus.POSTED,
                now, now, "Imbalanced test", "2026-11", null, null, "idem-1",
                List.of(debit, credit), MonetaryAmount.ZERO, now, "admin"
        ));

        assertTrue(ex.getMessage().contains("imbalanced"));
    }

    @Test
    @DisplayName("JournalEntry automatically sets totalAmount to equal debits upon balanced construction")
    void journalEntrySetsTotalAmountOnBalance() {
        JournalLine debit = new JournalLine(
                "acc-1", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("500.00"), MonetaryAmount.ZERO, "wire", CashFlowCategory.OPERATING
        );
        JournalLine credit = new JournalLine(
                "acc-2", "4010", "Sales", AccountType.REVENUE,
                MonetaryAmount.ZERO, MonetaryAmount.of("500.00"), "sales", CashFlowCategory.NONE
        );

        JournalEntry entry = new JournalEntry(
                "je-1", "JE-2026-000001", "REF-1", "STANDARD", EntryStatus.POSTED,
                now, now, "Balanced entry", "2026-11", null, null, "idem-1",
                List.of(debit, credit), MonetaryAmount.ZERO, now, "admin"
        );

        assertEquals(MonetaryAmount.of("500.00"), entry.totalAmount());
        assertEquals(EntryStatus.POSTED, entry.status());
    }
}