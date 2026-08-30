package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.lrn.repository.LearningEventInboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Replays locally retained GAP/projection-pending envelopes after their missing
 * prerequisite has arrived. This is a bounded, durable state machine rather
 * than an ACK-and-forget audit row.
 */
@Component
public class LearningReconciliationWorker {
    private static final int BATCH_SIZE = 50;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final LearningReliabilityRepository reliability;
    private final LearningReliableEventConsumer consumer;
    private final LearningEventInboxRepository inbox;
    private final String leaseOwner = "learning-reconciliation-" + UUID.randomUUID();

    public LearningReconciliationWorker(
            LearningReliabilityRepository reliability,
            LearningReliableEventConsumer consumer,
            LearningEventInboxRepository inbox
    ) {
        this.reliability = reliability;
        this.consumer = consumer;
        this.inbox = inbox;
    }

    @Scheduled(fixedDelayString = "${onlinejudge.reliability.reconciliation.fixed-delay-ms:5000}")
    public void reconcileDueMessages() {
        reconcileDue(Instant.now());
    }

    public int reconcileDue(Instant now) {
        var deferred = reliability.claimDeferred(leaseOwner, now, LEASE_DURATION, BATCH_SIZE);
        for (var record : deferred) {
            try {
                EventProcessingDecision decision = consumer.consume(consumer.deserialize(record.envelopeJson()));
                if (decision == EventProcessingDecision.ACK
                        && inbox.hasEvent(LearningReliableEventConsumer.CONSUMER, record.eventId())) {
                    reliability.markDeferredResolved(record.id(), leaseOwner, now);
                    reliability.resolveReconciliationForEvent(record.eventId(), now);
                } else if (decision == EventProcessingDecision.DEAD_LETTER) {
                    reliability.markDeferredFailed(record.id(), leaseOwner, "reconciliation event entered DLQ", now);
                } else {
                    reliability.releaseDeferred(record.id(), leaseOwner, now.plus(RETRY_DELAY), "prerequisite remains unavailable", now);
                }
            } catch (RuntimeException exception) {
                reliability.releaseDeferred(record.id(), leaseOwner, now.plus(RETRY_DELAY), safeMessage(exception), now);
            }
        }
        return deferred.size();
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
