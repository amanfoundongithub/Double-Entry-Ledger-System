package com.ledger_core.ledger_core.journal.infrastructure;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends MongoRepository<JournalEntryDocument, String> {

    Optional<JournalEntryDocument> findByEntryNumber(String entryNumber);

    Optional<JournalEntryDocument> findByIdempotencyKey(String idempotencyKey);

    List<JournalEntryDocument> findByLinesAccountIdOrderByPostingDateDesc(String accountId);

    long count();
}