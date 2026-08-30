package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Operators replay an exact, previously audited eventId only after the cause is
 * fixed.  The DLQ row is marked replayed only after broker confirmation.
 */
@Service
public class LearningDeadLetterReplayService {
    private final LearningReliabilityRepository reliability;
    private final LearningReliableEventConsumer envelopeCodec;
    private final ConfirmedEventPublisher publisher;

    public LearningDeadLetterReplayService(
            LearningReliabilityRepository reliability,
            LearningReliableEventConsumer envelopeCodec,
            ConfirmedEventPublisher publisher
    ) {
        this.reliability = reliability;
        this.envelopeCodec = envelopeCodec;
        this.publisher = publisher;
    }

    public boolean replay(String eventId, String operator) {
        if (eventId == null || eventId.isBlank() || operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("eventId and operator are required for controlled replay");
        }
        return reliability.findForReplay(LearningReliableEventConsumer.CONSUMER, eventId).map(deadLetter -> {
            ReliableEventEnvelope envelope = envelopeCodec.deserialize(deadLetter.envelopeJson());
            publisher.publish(envelope, "onlinejudge." + envelope.eventType());
            reliability.markReplayed(LearningReliableEventConsumer.CONSUMER, eventId, operator, Instant.now());
            return true;
        }).orElse(false);
    }
}
