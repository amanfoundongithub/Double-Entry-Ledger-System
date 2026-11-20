package com.ledger_core.ledger_core.periods.infrastructure;

import com.ledger_core.ledger_core.periods.domain.AccountingPeriod;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends MongoRepository<AccountingPeriod, String> {

    List<AccountingPeriod> findAllByOrderByStartDateAsc();

    Optional<AccountingPeriod> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Instant postingDateForStart,
            Instant postingDateForEnd
    );
}