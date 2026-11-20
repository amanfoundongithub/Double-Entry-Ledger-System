package com.ledger_core.ledger_core.accounts.application;

import com.ledger_core.ledger_core.accounts.api.AccountResponse;
import com.ledger_core.ledger_core.accounts.api.CreateAccountRequest;
import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import com.ledger_core.ledger_core.accounts.infrastructure.AccountRepository;
import com.ledger_core.ledger_core.shared.exceptions.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String normalizedCode = request.code().trim();
        if (accountRepository.existsByCode(normalizedCode)) {
            throw new DomainException("Account with code '" + normalizedCode + "' already exists");
        }

        Instant now = Instant.now(clock);
        Account account = Account.create(
                normalizedCode,
                request.name(),
                request.type(),
                request.normalSide(),
                request.cashFlowCategory(),
                request.isCashEquivalent(),
                request.isContra(),
                request.currency(),
                request.description(),
                now
        );

        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new DomainException("Account not found with id: " + id));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByCode(String code) {
        Account account = accountRepository.findByCode(code.trim())
                .orElseThrow(() -> new DomainException("Account not found with code: " + code));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(AccountType type, Boolean activeOnly) {
        List<Account> accounts;
        if (type != null && Boolean.TRUE.equals(activeOnly)) {
            accounts = accountRepository.findByTypeAndActiveTrueOrderByCodeAsc(type);
        } else if (type != null) {
            accounts = accountRepository.findByTypeOrderByCodeAsc(type);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            accounts = accountRepository.findByActiveTrueOrderByCodeAsc();
        } else {
            accounts = accountRepository.findAllByOrderByCodeAsc();
        }

        return accounts.stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse deactivateAccount(String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new DomainException("Account not found with id: " + id));

        if (!account.isActive()) {
            return AccountResponse.from(account);
        }

        account.deactivate(Instant.now(clock));
        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }
}