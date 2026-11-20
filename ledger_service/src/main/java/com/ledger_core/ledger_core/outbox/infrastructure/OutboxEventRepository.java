package com.ledger_core.ledger_core.outbox.infrastructure;

import com.ledger_core.ledger_core.outbox.domain.OutboxEvent;
import com.ledger_core.ledger_core.outbox.domain.OutboxStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}