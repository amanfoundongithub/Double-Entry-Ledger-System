package com.ledger_core.ledger_core.reporting.application;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.domain.CashFlowCategory;
import com.ledger_core.ledger_core.accounts.domain.NormalSide;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.journal.domain.EntryStatus;
import com.ledger_core.ledger_core.reporting.api.BalanceSheetResponse;
import com.ledger_core.ledger_core.reporting.api.CashFlowLine;
import com.ledger_core.ledger_core.reporting.api.CashFlowStatementResponse;
import com.ledger_core.ledger_core.reporting.api.IncomeStatementResponse;
import com.ledger_core.ledger_core.reporting.api.StatementLine;
import com.ledger_core.ledger_core.reporting.api.TrialBalanceResponse;
import com.ledger_core.ledger_core.reporting.api.TrialBalanceRow;
import com.ledger_core.ledger_core.shared.money.MonetaryAmount;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatementEngineService {

    private final MongoTemplate mongoTemplate;
    private final AccountRepository accountRepository;

    public StatementEngineService(MongoTemplate mongoTemplate, AccountRepository accountRepository) {
        this.mongoTemplate = mongoTemplate;
        this.accountRepository = accountRepository;
    }

    private record RawAccountTotals(
            String _id, // lines.accountId
            Decimal128 totalDebits,
            Decimal128 totalCredits
    ) {
        MonetaryAmount debits() {
            return totalDebits == null ? MonetaryAmount.ZERO : MonetaryAmount.of(totalDebits.bigDecimalValue());
        }

        MonetaryAmount credits() {
            return totalCredits == null ? MonetaryAmount.ZERO : MonetaryAmount.of(totalCredits.bigDecimalValue());
        }
    }

    private record AccountActivity(
            Account account,
            MonetaryAmount debits,
            MonetaryAmount credits
    ) {
        MonetaryAmount signedNormalBalance() {
            NormalSide effectiveSide = account.isContra()
                    ? account.getNormalSide().opposite()
                    : account.getNormalSide();

            return effectiveSide == NormalSide.DEBIT
                    ? debits.subtract(credits)
                    : credits.subtract(debits);
        }

        MonetaryAmount statementAmount() {
            if (account.getType() == AccountType.REVENUE) {
                return credits.subtract(debits);
            }
            if (account.getType() == AccountType.EXPENSE) {
                return debits.subtract(credits);
            }
            if (account.isContra()) {
                // Contra-assets show as deductions in Asset section; contra-liabilities/equity similarly
                NormalSide naturalSide = account.getType().defaultNormalSide();
                MonetaryAmount raw = naturalSide == NormalSide.DEBIT
                        ? debits.subtract(credits)
                        : credits.subtract(debits);
                return raw.negate();
            }
            return signedNormalBalance();
        }
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(Instant asOfDate) {
        List<AccountActivity> activities = aggregateActivity(null, asOfDate);

        List<TrialBalanceRow> rows = activities.stream()
                .map(act -> new TrialBalanceRow(
                        act.account().getId(),
                        act.account().getCode(),
                        act.account().getName(),
                        act.account().getType(),
                        act.account().getNormalSide(),
                        act.account().isContra(),
                        act.debits().value(),
                        act.credits().value(),
                        act.signedNormalBalance().value()
                ))
                .toList();

        MonetaryAmount totalDebits = activities.stream()
                .map(AccountActivity::debits)
                .reduce(MonetaryAmount.ZERO, MonetaryAmount::add);

        MonetaryAmount totalCredits = activities.stream()
                .map(AccountActivity::credits)
                .reduce(MonetaryAmount.ZERO, MonetaryAmount::add);

        return new TrialBalanceResponse(
                asOfDate,
                rows,
                totalDebits.value(),
                totalCredits.value(),
                totalDebits.compareTo(totalCredits) == 0
        );
    }

    @Transactional(readOnly = true)
    public IncomeStatementResponse getIncomeStatement(Instant startDate, Instant endDate) {
        List<AccountActivity> activities = aggregateActivity(startDate, endDate);

        List<StatementLine> revenues = new ArrayList<>();
        List<StatementLine> expenses = new ArrayList<>();
        MonetaryAmount totalRevenue = MonetaryAmount.ZERO;
        MonetaryAmount totalExpenses = MonetaryAmount.ZERO;

        for (AccountActivity act : activities) {
            AccountType type = act.account().getType();
            if (type == AccountType.REVENUE) {
                MonetaryAmount amt = act.statementAmount();
                revenues.add(toStatementLine(act.account(), amt));
                totalRevenue = totalRevenue.add(amt);
            } else if (type == AccountType.EXPENSE) {
                MonetaryAmount amt = act.statementAmount();
                expenses.add(toStatementLine(act.account(), amt));
                totalExpenses = totalExpenses.add(amt);
            }
        }

        MonetaryAmount netIncome = totalRevenue.subtract(totalExpenses);

        return new IncomeStatementResponse(
                startDate,
                endDate,
                revenues,
                expenses,
                totalRevenue.value(),
                totalExpenses.value(),
                netIncome.value()
        );
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse getBalanceSheet(Instant asOfDate) {
        List<AccountActivity> allTimeActivities = aggregateActivity(null, asOfDate);

        List<StatementLine> assets = new ArrayList<>();
        List<StatementLine> liabilities = new ArrayList<>();
        List<StatementLine> equity = new ArrayList<>();

        MonetaryAmount totalAssets = MonetaryAmount.ZERO;
        MonetaryAmount totalLiabilities = MonetaryAmount.ZERO;
        MonetaryAmount totalEquity = MonetaryAmount.ZERO;

        MonetaryAmount cumulativeRevenue = MonetaryAmount.ZERO;
        MonetaryAmount cumulativeExpense = MonetaryAmount.ZERO;

        for (AccountActivity act : allTimeActivities) {
            Account account = act.account();
            AccountType type = account.getType();

            switch (type) {
                case ASSET -> {
                    MonetaryAmount amt = act.statementAmount();
                    assets.add(toStatementLine(account, amt));
                    totalAssets = totalAssets.add(amt);
                }
                case LIABILITY -> {
                    MonetaryAmount amt = act.statementAmount();
                    liabilities.add(toStatementLine(account, amt));
                    totalLiabilities = totalLiabilities.add(amt);
                }
                case EQUITY -> {
                    MonetaryAmount amt = act.statementAmount();
                    equity.add(toStatementLine(account, amt));
                    totalEquity = totalEquity.add(amt);
                }
                case REVENUE -> cumulativeRevenue = cumulativeRevenue.add(act.statementAmount());
                case EXPENSE -> cumulativeExpense = cumulativeExpense.add(act.statementAmount());
            }
        }

        MonetaryAmount unclosedNetIncome = cumulativeRevenue.subtract(cumulativeExpense);
        totalEquity = totalEquity.add(unclosedNetIncome);

        boolean balanced = totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;

        return new BalanceSheetResponse(
                asOfDate,
                assets,
                liabilities,
                equity,
                unclosedNetIncome.value(),
                totalAssets.value(),
                totalLiabilities.value(),
                totalEquity.value(),
                balanced
        );
    }

    @Transactional(readOnly = true)
public CashFlowStatementResponse getCashFlowStatement(Instant startDate, Instant endDate) {
    Map<String, Account> accountsById = accountRepository.findAll().stream()
            .collect(Collectors.toMap(Account::getId, Function.identity()));

    Set<String> cashAccountIds = accountsById.values().stream()
            .filter(Account::isCashEquivalent)
            .map(Account::getId)
            .collect(Collectors.toSet());

    MonetaryAmount beginningCash = startDate != null
            ? computeCashBalanceBefore(startDate, cashAccountIds)
            : MonetaryAmount.ZERO;

    Criteria periodCriteria = Criteria.where("status").is(EntryStatus.POSTED.name());
    if (startDate != null && endDate != null) {
        periodCriteria = periodCriteria.and("postingDate").gte(startDate).lte(endDate);
    } else if (startDate != null) {
        periodCriteria = periodCriteria.and("postingDate").gte(startDate);
    } else if (endDate != null) {
        periodCriteria = periodCriteria.and("postingDate").lte(endDate);
    }

    Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(periodCriteria),
            Aggregation.unwind("lines"),
            Aggregation.match(Criteria.where("lines.accountId").in(cashAccountIds)),
            Aggregation.group("lines.accountId", "lines.cashFlowCategory")
                    .sum("lines.debitAmount").as("totalDebits")
                    .sum("lines.creditAmount").as("totalCredits")
    );

    AggregationResults<RawCashFlowTotals> results = mongoTemplate.aggregate(
            aggregation,
            "journal_entries",
            RawCashFlowTotals.class
    );

    List<CashFlowLine> operatingLines = new ArrayList<>();
    List<CashFlowLine> investingLines = new ArrayList<>();
    List<CashFlowLine> financingLines = new ArrayList<>();

    MonetaryAmount operatingTotal = MonetaryAmount.ZERO;
    MonetaryAmount investingTotal = MonetaryAmount.ZERO;
    MonetaryAmount financingTotal = MonetaryAmount.ZERO;
    MonetaryAmount otherCashTotal = MonetaryAmount.ZERO;

    for (RawCashFlowTotals raw : results.getMappedResults()) {
        Account cashAccount = accountsById.get(raw.accountId());
        if (cashAccount == null) {
            continue;
        }

        // Cash is a DEBIT-normal asset: inflows are debits, outflows are credits
        MonetaryAmount netCash = raw.debits().subtract(raw.credits());
        CashFlowCategory category = raw.category() != null ? raw.category() : cashAccount.getCashFlowCategory();

        switch (category) {
            case OPERATING -> {
                operatingLines.add(new CashFlowLine(cashAccount.getId(), cashAccount.getCode(), cashAccount.getName(), category, netCash.value()));
                operatingTotal = operatingTotal.add(netCash);
            }
            case INVESTING -> {
                investingLines.add(new CashFlowLine(cashAccount.getId(), cashAccount.getCode(), cashAccount.getName(), category, netCash.value()));
                investingTotal = investingTotal.add(netCash);
            }
            case FINANCING -> {
                financingLines.add(new CashFlowLine(cashAccount.getId(), cashAccount.getCode(), cashAccount.getName(), category, netCash.value()));
                financingTotal = financingTotal.add(netCash);
            }
            case NONE -> otherCashTotal = otherCashTotal.add(netCash);
        }
    }

    operatingLines.sort(Comparator.comparing(CashFlowLine::accountCode));
    investingLines.sort(Comparator.comparing(CashFlowLine::accountCode));
    financingLines.sort(Comparator.comparing(CashFlowLine::accountCode));

    MonetaryAmount netCashChange = operatingTotal.add(investingTotal).add(financingTotal).add(otherCashTotal);
    MonetaryAmount endingCashBalance = beginningCash.add(netCashChange);
    MonetaryAmount actualEndingCash = endDate != null
            ? computeCashBalanceUpTo(endDate, cashAccountIds)
            : computeCashBalanceAllTime(cashAccountIds);

    boolean reconciled = endingCashBalance.compareTo(actualEndingCash) == 0;

    return new CashFlowStatementResponse(
            startDate,
            endDate,
            operatingLines,
            investingLines,
            financingLines,
            operatingTotal.value(),
            investingTotal.value(),
            financingTotal.value(),
            netCashChange.value(),
            beginningCash.value(),
            endingCashBalance.value(),
            reconciled
    );
}

    private List<AccountActivity> aggregateActivity(Instant startDate, Instant endDate) {
        Criteria entryCriteria = Criteria.where("status").is(EntryStatus.POSTED.name());

        if (startDate != null && endDate != null) {
            entryCriteria = entryCriteria.and("postingDate").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            entryCriteria = entryCriteria.and("postingDate").gte(startDate);
        } else if (endDate != null) {
            entryCriteria = entryCriteria.and("postingDate").lte(endDate);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(entryCriteria),
                Aggregation.unwind("lines"),
                Aggregation.group("lines.accountId")
                        .sum("lines.debitAmount").as("totalDebits")
                        .sum("lines.creditAmount").as("totalCredits")
        );

        AggregationResults<RawAccountTotals> results = mongoTemplate.aggregate(
                aggregation,
                "journal_entries",
                RawAccountTotals.class
        );

        Map<String, RawAccountTotals> totalsByAccountId = results.getMappedResults().stream()
                .collect(Collectors.toMap(RawAccountTotals::_id, Function.identity()));

        Map<String, Account> accountsById = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<AccountActivity> list = new ArrayList<>();
        for (Map.Entry<String, RawAccountTotals> entry : totalsByAccountId.entrySet()) {
            Account account = accountsById.get(entry.getKey());
            if (account != null) {
                list.add(new AccountActivity(
                        account,
                        entry.getValue().debits(),
                        entry.getValue().credits()
                ));
            }
        }

        list.sort(Comparator.comparing(a -> a.account().getCode()));
        return list;
    }

    private StatementLine toStatementLine(Account account, MonetaryAmount amount) {
        return new StatementLine(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.isContra(),
                amount.value()
        );
    }

    private record RawCashFlowTotals(
        CashFlowId _id,
        Decimal128 totalDebits,
        Decimal128 totalCredits
) {
    record CashFlowId(String accountId, CashFlowCategory cashFlowCategory) {
    }

    String accountId() {
        return _id != null ? _id.accountId() : null;
    }

    CashFlowCategory category() {
        return _id != null ? _id.cashFlowCategory() : CashFlowCategory.NONE;
    }

    MonetaryAmount debits() {
        return totalDebits == null ? MonetaryAmount.ZERO : MonetaryAmount.of(totalDebits.bigDecimalValue());
    }

    MonetaryAmount credits() {
        return totalCredits == null ? MonetaryAmount.ZERO : MonetaryAmount.of(totalCredits.bigDecimalValue());
    }
}

private MonetaryAmount computeCashBalanceBefore(Instant cutoffExclusive, Set<String> cashAccountIds) {
    if (cashAccountIds.isEmpty()) {
        return MonetaryAmount.ZERO;
    }
    Criteria criteria = Criteria.where("status").is(EntryStatus.POSTED.name())
            .and("postingDate").lt(cutoffExclusive);
    return sumCashNetMovement(criteria, cashAccountIds);
}

private MonetaryAmount computeCashBalanceUpTo(Instant cutoffInclusive, Set<String> cashAccountIds) {
    if (cashAccountIds.isEmpty()) {
        return MonetaryAmount.ZERO;
    }
    Criteria criteria = Criteria.where("status").is(EntryStatus.POSTED.name())
            .and("postingDate").lte(cutoffInclusive);
    return sumCashNetMovement(criteria, cashAccountIds);
}

private MonetaryAmount computeCashBalanceAllTime(Set<String> cashAccountIds) {
    if (cashAccountIds.isEmpty()) {
        return MonetaryAmount.ZERO;
    }
    Criteria criteria = Criteria.where("status").is(EntryStatus.POSTED.name());
    return sumCashNetMovement(criteria, cashAccountIds);
}

private MonetaryAmount sumCashNetMovement(Criteria entryCriteria, Set<String> cashAccountIds) {
    Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(entryCriteria),
            Aggregation.unwind("lines"),
            Aggregation.match(Criteria.where("lines.accountId").in(cashAccountIds)),
            Aggregation.group()
                    .sum("lines.debitAmount").as("totalDebits")
                    .sum("lines.creditAmount").as("totalCredits")
    );
    AggregationResults<RawAccountTotals> res = mongoTemplate.aggregate(agg, "journal_entries", RawAccountTotals.class);
    RawAccountTotals mapped = res.getUniqueMappedResult();
    if (mapped == null) {
        return MonetaryAmount.ZERO;
    }
    return mapped.debits().subtract(mapped.credits());
}
}