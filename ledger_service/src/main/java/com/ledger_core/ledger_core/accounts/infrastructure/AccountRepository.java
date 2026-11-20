package com.ledger_core.ledger_core.accounts.infrastructure;

import com.ledger_core.ledger_core.accounts.domain.Account;
import com.ledger_core.ledger_core.accounts.domain.AccountType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends MongoRepository<Account, String> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    List<Account> findAllByOrderByCodeAsc();

    List<Account> findByTypeOrderByCodeAsc(AccountType type);

    List<Account> findByActiveTrueOrderByCodeAsc();

    List<Account> findByTypeAndActiveTrueOrderByCodeAsc(AccountType type);
}