package com.ledger_core.ledger_core.integration;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.reporting.api.BalanceSheetResponse;
import com.ledger_core.ledger_core.reporting.api.IncomeStatementResponse;
import com.ledger_core.ledger_core.reporting.api.TrialBalanceResponse;
import com.ledger_core.ledger_core.reporting.application.StatementEngineService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementEngineIntegrationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AccountRepository accountRepository;

    private StatementEngineService statementEngineService;

    private Account cashAccount;
    private Account arAccount;
    private Account revenueAccount;
    private Account expenseAccount;

    private record AccountTotalInput(String accountId, Decimal128 debits, Decimal128 credits) {
    }

    @BeforeEach
    void setUp() {
        statementEngineService = new StatementEngineService(mongoTemplate, accountRepository);

        Instant created = Instant.parse("2026-11-01T00:00:00Z");
        cashAccount = new Account("acc-1010", "1010", "Bank", AccountType.ASSET, NormalSide.DEBIT,
                CashFlowCategory.OPERATING, true, false, "INR", true, null, created, created);
        arAccount = new Account("acc-1200", "1200", "Accounts Receivable", AccountType.ASSET, NormalSide.DEBIT,
                CashFlowCategory.NONE, false, false, "INR", true, null, created, created);
        revenueAccount = new Account("acc-4010", "4010", "Consulting Revenue", AccountType.REVENUE, NormalSide.CREDIT,
                CashFlowCategory.NONE, false, false, "INR", true, null, created, created);
        expenseAccount = new Account("acc-5010", "5010", "Office Rent", AccountType.EXPENSE, NormalSide.DEBIT,
                CashFlowCategory.OPERATING, false, false, "INR", true, null, created, created);

        when(accountRepository.findAll()).thenReturn(List.of(cashAccount, arAccount, revenueAccount, expenseAccount));
    }

    private void stubAccountTotals(List<AccountTotalInput> inputs) {
        try {
            Class<?> rawTotalsClass = Class.forName(
                    "com.ledger_core.ledger_core.reporting.application.StatementEngineService$RawAccountTotals"
            );
            var ctor = rawTotalsClass.getDeclaredConstructor(String.class, Decimal128.class, Decimal128.class);
            ctor.setAccessible(true);

            List<Object> mapped = new ArrayList<>();
            for (AccountTotalInput in : inputs) {
                mapped.add(ctor.newInstance(in.accountId(), in.debits(), in.credits()));
            }

            AggregationResults<Object> results = new AggregationResults<>(mapped, new Document());
            doReturn(results).when(mongoTemplate).aggregate(any(Aggregation.class), eq("journal_entries"), any());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Income Statement computes Revenue minus Expenses accurately")
    void computesIncomeStatementNetIncome() {
        List<AccountTotalInput> rows = List.of(
                new AccountTotalInput("acc-1010", new Decimal128(new BigDecimal("40000.00")), new Decimal128(BigDecimal.ZERO)),
                new AccountTotalInput("acc-1200", new Decimal128(new BigDecimal("85000.00")), new Decimal128(BigDecimal.ZERO)),
                new AccountTotalInput("acc-4010", new Decimal128(BigDecimal.ZERO), new Decimal128(new BigDecimal("100000.00"))),
                new AccountTotalInput("acc-5010", new Decimal128(new BigDecimal("25000.00")), new Decimal128(BigDecimal.ZERO))
        );
        stubAccountTotals(rows);

        IncomeStatementResponse pnl = statementEngineService.getIncomeStatement(
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-30T23:59:59Z")
        );

        assertEquals(new BigDecimal("100000.00"), pnl.totalRevenue());
        assertEquals(new BigDecimal("25000.00"), pnl.totalExpenses());
        assertEquals(new BigDecimal("75000.00"), pnl.netIncome());
    }

    @Test
    @DisplayName("Balance Sheet satisfies Assets = Liabilities + Equity including unclosed net income")
    void balanceSheetSatisfiesFundamentalEquality() {
        Account equityAccount = new Account("acc-3010", "3010", "Owner Capital", AccountType.EQUITY, NormalSide.CREDIT,
                CashFlowCategory.FINANCING, false, false, "INR", true, null,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));

        when(accountRepository.findAll()).thenReturn(
                List.of(cashAccount, arAccount, revenueAccount, expenseAccount, equityAccount)
        );

        List<AccountTotalInput> rows = List.of(
                new AccountTotalInput("acc-1010", new Decimal128(new BigDecimal("40000.00")), new Decimal128(BigDecimal.ZERO)),
                new AccountTotalInput("acc-1200", new Decimal128(new BigDecimal("85000.00")), new Decimal128(BigDecimal.ZERO)),
                new AccountTotalInput("acc-3010", new Decimal128(BigDecimal.ZERO), new Decimal128(new BigDecimal("50000.00"))),
                new AccountTotalInput("acc-4010", new Decimal128(BigDecimal.ZERO), new Decimal128(new BigDecimal("100000.00"))),
                new AccountTotalInput("acc-5010", new Decimal128(new BigDecimal("25000.00")), new Decimal128(BigDecimal.ZERO))
        );
        stubAccountTotals(rows);

        BalanceSheetResponse bs = statementEngineService.getBalanceSheet(Instant.parse("2026-11-30T23:59:59Z"));

        assertEquals(new BigDecimal("125000.00"), bs.totalAssets());
        assertEquals(new BigDecimal("75000.00"), bs.cumulativeUnclosedNetIncome());
        assertEquals(new BigDecimal("125000.00"), bs.totalEquity());
        assertTrue(bs.balanced());
    }

    @Test
    @DisplayName("Trial Balance sums total debits and total credits across accounts")
    void trialBalanceTotalsMatch() {
        List<AccountTotalInput> rows = List.of(
                new AccountTotalInput("acc-1010", new Decimal128(new BigDecimal("10000.00")), new Decimal128(BigDecimal.ZERO)),
                new AccountTotalInput("acc-4010", new Decimal128(BigDecimal.ZERO), new Decimal128(new BigDecimal("10000.00")))
        );
        stubAccountTotals(rows);

        TrialBalanceResponse tb = statementEngineService.getTrialBalance(Instant.parse("2026-11-30T23:59:59Z"));

        assertEquals(new BigDecimal("10000.00"), tb.totalDebits());
        assertEquals(new BigDecimal("10000.00"), tb.totalCredits());
        assertTrue(tb.balanced());
    }
}