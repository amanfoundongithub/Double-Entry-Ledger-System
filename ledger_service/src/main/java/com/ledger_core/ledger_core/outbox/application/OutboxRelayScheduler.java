package com.ledger_core.ledger_core.outbox.application;

import com.ledger_core.ledger_core.outbox.domain.OutboxEvent;
import com.ledger_core.ledger_core.outbox.domain.OutboxStatus;
import com.ledger_core.ledger_core.outbox.infrastructure.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository outboxRepository;
    private final ApplicationEventPublisher springEventPublisher;
    private final Clock clock;
    private final int maxRetries;

    public OutboxRelayScheduler(
            OutboxEventRepository outboxRepository,
            ApplicationEventPublisher springEventPublisher,
            Clock clock,
            @Value("${ledger-core.outbox.max-retries:5}") int maxRetries
    ) {
        this.outboxRepository = outboxRepository;
        this.springEventPublisher = springEventPublisher;
        this.clock = clock;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${ledger-core.outbox.poll-delay:2000ms}")
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : batch) {
            Instant now = Instant.now(clock);
            try {
                springEventPublisher.publishEvent(event);
                event.markPublished(now);
                outboxRepository.save(event);
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event id={} type={}: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
                event.recordFailure(maxRetries, now);
                outboxRepository.save(event);
            }
        }
    }
}