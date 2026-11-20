package com.ledger_core.ledger_core.journal.scenarios;

import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.journal.domain.JournalEntry;
import com.ledger_core.ledger_core.journal.domain.JournalLine;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComplexScenariosTest {

    private final Instant timestamp = Instant.parse("2026-11-20T14:30:00Z");

    @Test
    @DisplayName("Scenario A: Partly cash (₹40,000) and partly credit (₹60,000) sale")
    void testScenarioAPartlyCashPartlyCreditSale() {
        JournalLine line1 = new JournalLine(
                "acc-1010", "1010", "Bank Account", AccountType.ASSET,
                MonetaryAmount.of("40000.00"), MonetaryAmount.ZERO,
                "40% cash upfront", CashFlowCategory.OPERATING
        );
        JournalLine line2 = new JournalLine(
                "acc-1200", "1200", "Accounts Receivable", AccountType.ASSET,
                MonetaryAmount.of("60000.00"), MonetaryAmount.ZERO,
                "60% Net 30 terms", CashFlowCategory.NONE
        );
        JournalLine line3 = new JournalLine(
                "acc-4010", "4010", "Sales Revenue", AccountType.REVENUE,
                MonetaryAmount.ZERO, MonetaryAmount.of("100000.00"),
                "Total invoice value", CashFlowCategory.NONE
        );

        JournalEntry entry = new JournalEntry(
                "je-001", "JE-2026-000412", "INV-2026-904", "CREDIT_SALE", EntryStatus.POSTED,
                timestamp, timestamp, "Consulting engagement sale", "2026-11",
                null, null, "idem-scen-a",
                List.of(line1, line2, line3), MonetaryAmount.ZERO, timestamp, "usr_admin"
        );

        assertEquals(MonetaryAmount.of("100000.00"), entry.totalAmount());
        assertEquals(MonetaryAmount.of("100000.00"), JournalEntry.totalDebits(entry.lines()));
        assertEquals(MonetaryAmount.of("100000.00"), JournalEntry.totalCredits(entry.lines()));
    }

    @Test
    @DisplayName("Scenario B: Sale of Equipment with Accumulated Depreciation (₹80k), Cash (₹150k), and Gain (₹30k)")
    void testScenarioBSaleOfEquipmentWithDepreciationAndGain() {
        // Line 1: Cash received (Inflow to Bank)
        JournalLine line1 = new JournalLine(
                "acc-1010", "1010", "Bank Account", AccountType.ASSET,
                MonetaryAmount.of("150000.00"), MonetaryAmount.ZERO,
                "Cash received from buyer", CashFlowCategory.INVESTING
        );
        // Line 2: Derecognize Accumulated Depreciation (Contra-Asset debit to clear credit balance)
        JournalLine line2 = new JournalLine(
                "acc-1510", "1510", "Accumulated Depreciation", AccountType.ASSET,
                MonetaryAmount.of("80000.00"), MonetaryAmount.ZERO,
                "Clear accumulated depreciation", CashFlowCategory.NONE
        );
        // Line 3: Derecognize Fixed Asset at gross historical cost
        JournalLine line3 = new JournalLine(
                "acc-1500", "1500", "Equipment", AccountType.ASSET,
                MonetaryAmount.ZERO, MonetaryAmount.of("200000.00"),
                "Remove equipment historical cost", CashFlowCategory.NONE
        );
        // Line 4: Recognize Gain on Disposal
        JournalLine line4 = new JournalLine(
                "acc-4900", "4900", "Gain on Sale of Fixed Assets", AccountType.REVENUE,
                MonetaryAmount.ZERO, MonetaryAmount.of("30000.00"),
                "Gain on equipment disposal", CashFlowCategory.NONE
        );

        JournalEntry entry = new JournalEntry(
                "je-002", "JE-2026-000413", "DISP-2026-01", "ASSET_DISPOSAL", EntryStatus.POSTED,
                timestamp, timestamp, "Disposal of printing equipment", "2026-11",
                null, null, "idem-scen-b",
                List.of(line1, line2, line3, line4), MonetaryAmount.ZERO, timestamp, "usr_admin"
        );

        // Sum of Debits: 150,000 + 80,000 = 230,000
        // Sum of Credits: 200,000 + 30,000 = 230,000
        assertEquals(MonetaryAmount.of("230000.00"), entry.totalAmount());
        assertEquals(MonetaryAmount.of("230000.00"), JournalEntry.totalDebits(entry.lines()));
        assertEquals(MonetaryAmount.of("230000.00"), JournalEntry.totalCredits(entry.lines()));
    }

    @Test
    @DisplayName("Scenario C: Immutable reversal mirrors debit/credit amounts and flags original as REVERSED")
    void testScenarioCImmutableReversal() {
        JournalLine origDebit = new JournalLine(
                "acc-1010", "1010", "Bank", AccountType.ASSET,
                MonetaryAmount.of("5000.00"), MonetaryAmount.ZERO, "Original wire", CashFlowCategory.OPERATING
        );
        JournalLine origCredit = new JournalLine(
                "acc-4010", "4010", "Sales", AccountType.REVENUE,
                MonetaryAmount.ZERO, MonetaryAmount.of("5000.00"), "Original sale", CashFlowCategory.NONE
        );

        JournalEntry original = new JournalEntry(
                "je-orig-01", "JE-2026-000500", "INV-500", "SALE", EntryStatus.POSTED,
                timestamp, timestamp, "Original erroneous entry", "2026-11",
                null, null, "idem-orig",
                List.of(origDebit, origCredit), MonetaryAmount.ZERO, timestamp, "usr_admin"
        );

        // Create mirror lines
        List<JournalLine> reversedLines = original.lines().stream()
                .map(JournalLine::reversedCopy)
                .toList();

        // Line 1 mirror: was Debit 5000 -> becomes Credit 5000
        assertEquals(MonetaryAmount.ZERO, reversedLines.get(0).debitAmount());
        assertEquals(MonetaryAmount.of("5000.00"), reversedLines.get(0).creditAmount());

        // Line 2 mirror: was Credit 5000 -> becomes Debit 5000
        assertEquals(MonetaryAmount.of("5000.00"), reversedLines.get(1).debitAmount());
        assertEquals(MonetaryAmount.ZERO, reversedLines.get(1).creditAmount());

        // Reversal entry
        JournalEntry reversal = new JournalEntry(
                "je-rev-01", "JE-2026-000501", original.reference(), "REVERSAL", EntryStatus.POSTED,
                timestamp, timestamp, "Reversal of JE-2026-000500", "2026-11",
                original.id(), null, "idem-rev",
                reversedLines, MonetaryAmount.ZERO, timestamp, "usr_admin"
        );

        // Original entry marked reversed
        JournalEntry markedOriginal = original.markReversed(reversal.id());

        assertEquals(EntryStatus.REVERSED, markedOriginal.status());
        assertEquals("je-rev-01", markedOriginal.reversedByEntryId());
        assertEquals("je-orig-01", reversal.reversalOfEntryId());

        // Verify that trying to reverse an already-reversed entry throws DomainException
        assertThrows(DomainException.class, () -> markedOriginal.markReversed("je-rev-02"));
    }
}