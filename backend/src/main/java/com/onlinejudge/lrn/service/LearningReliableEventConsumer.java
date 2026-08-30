package com.onlinejudge.lrn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.NonRetryableEventException;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.LearningEventInboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implements at-least-once consumption.  The inbox row and local notification
 * side effect share one transaction; a lost manual ACK therefore re-delivers a
 * successful no-op instead of creating another notification.
 */
@Component
public class LearningReliableEventConsumer {
    public static final String CONSUMER = "learning";

    private final LearningEventInboxRepository inbox;
    private final LearningReliabilityRepository reliability;
    private final LearningHomeworkPublishedHandler homeworkHandler;
    private final LearningCourseMemberChangedHandler courseMemberHandler;
    private final LearningCourseMembershipSnapshotHandler courseMembershipSnapshotHandler;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate localTransaction;
    private final TransactionTemplate recoveryTransaction;
    private final int maxAttempts;

    public LearningReliableEventConsumer(
            LearningEventInboxRepository inbox,
            LearningReliabilityRepository reliability,
            LearningHomeworkPublishedHandler homeworkHandler,
            LearningCourseMemberChangedHandler courseMemberHandler,
            LearningCourseMembershipSnapshotHandler courseMembershipSnapshotHandler,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${onlinejudge.reliability.consumer.max-attempts:3}") int maxAttempts
    ) {
        this.inbox = inbox;
        this.reliability = reliability;
        this.homeworkHandler = homeworkHandler;
        this.courseMemberHandler = courseMemberHandler;
        this.courseMembershipSnapshotHandler = courseMembershipSnapshotHandler;
        this.objectMapper = objectMapper;
        this.localTransaction = new TransactionTemplate(transactionManager);
        this.recoveryTransaction = new TransactionTemplate(transactionManager);
        this.recoveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public EventProcessingDecision consume(ReliableEventEnvelope envelope) {
        try {
            envelope.requireV2();
            return localTransaction.execute(status -> consumeInLocalTransaction(envelope));
        } catch (CourseProjectionUnavailableException exception) {
            persistDeferred(envelope, "MEMBERSHIP_PROJECTION_PENDING");
            return EventProcessingDecision.ACK;
        } catch (NonRetryableEventException exception) {
            persistDeadLetter(envelope, "NON_RETRYABLE", exception.getMessage(), 0);
            return EventProcessingDecision.DEAD_LETTER;
        } catch (RuntimeException exception) {
            int attempts = recoveryTransaction.execute(status -> inbox.recordAttempt(
                    CONSUMER, envelope.eventId(), safeMessage(exception), Instant.now()));
            if (attempts >= maxAttempts) {
                persistDeadLetter(envelope, "RETRY_EXHAUSTED", safeMessage(exception), attempts);
                return EventProcessingDecision.DEAD_LETTER;
            }
            return EventProcessingDecision.RETRY;
        }
    }

    private EventProcessingDecision consumeInLocalTransaction(ReliableEventEnvelope envelope) {
        if (inbox.hasEvent(CONSUMER, envelope.eventId())) {
            return EventProcessingDecision.ACK;
        }
        long current = inbox.lastAppliedAggregateVersion(
                CONSUMER, envelope.aggregateType(), envelope.aggregateId());
        if (envelope.aggregateVersion() <= current) {
            inbox.record(CONSUMER, envelope, "IGNORED_OLD", Instant.now());
            return EventProcessingDecision.ACK;
        }
        // A Course roster snapshot is a complete replacement fact, not a
        // partial delta.  It is the only aggregate allowed to fast-forward a
        // restored Learning projection: Course reconciliation emits vN+1
        // after Learning may have lost v1..vN, and waiting for those retired
        // historical facts would leave every deferred homework permanent.
        // Ordinary events, including course.member.changed.v2, still require
        // strict contiguous ordering below.
        if (isAuthoritativeCourseRosterSnapshot(envelope)) {
            apply(envelope);
            inbox.record(CONSUMER, envelope, "APPLIED", Instant.now());
            return EventProcessingDecision.ACK;
        }
        if (envelope.aggregateVersion() > current + 1) {
            persistDeferred(envelope, "PROJECTION_GAP");
            reliability.recordGap(
                    CONSUMER, envelope.aggregateType(), envelope.aggregateId(), envelope.aggregateVersion(), current,
                    envelope.eventId(), envelope.correlationId(), Instant.now());
            return EventProcessingDecision.ACK;
        }
        apply(envelope);
        inbox.record(CONSUMER, envelope, "APPLIED", Instant.now());
        return EventProcessingDecision.ACK;
    }

    private boolean isAuthoritativeCourseRosterSnapshot(ReliableEventEnvelope envelope) {
        return LearningCourseMembershipSnapshotHandler.EVENT_TYPE.equals(envelope.eventType())
                && LearningCourseMembershipSnapshotHandler.AGGREGATE_TYPE.equals(envelope.aggregateType());
    }

    private void apply(ReliableEventEnvelope envelope) {
        if ("assessment.homework.published.v2".equals(envelope.eventType())) {
            homeworkHandler.apply(envelope);
            return;
        }
        if ("course.member.changed.v2".equals(envelope.eventType())) {
            courseMemberHandler.apply(envelope);
            return;
        }
        if (LearningCourseMembershipSnapshotHandler.EVENT_TYPE.equals(envelope.eventType())) {
            courseMembershipSnapshotHandler.apply(envelope);
            return;
        }
        throw new NonRetryableEventException("Learning does not consume eventType " + envelope.eventType());
    }

    public ReliableEventEnvelope deserialize(String json) {
        try {
            var root = objectMapper.readTree(json);
            return new ReliableEventEnvelope(
                    required(root, "eventId"),
                    required(root, "eventType"),
                    root.path("payloadVersion").asInt(),
                    required(root, "aggregateType"),
                    required(root, "aggregateId"),
                    root.path("aggregateVersion").asLong(),
                    Instant.parse(required(root, "occurredAt")),
                    required(root, "correlationId"),
                    root.path("payload")
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new NonRetryableEventException("invalid EventEnvelope: " + safeMessage(exception));
        }
    }

    public String serialize(ReliableEventEnvelope envelope) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("eventId", envelope.eventId());
            root.put("eventType", envelope.eventType());
            root.put("payloadVersion", envelope.payloadVersion());
            root.put("aggregateType", envelope.aggregateType());
            root.put("aggregateId", envelope.aggregateId());
            root.put("aggregateVersion", envelope.aggregateVersion());
            root.put("occurredAt", envelope.occurredAt().toString());
            root.put("correlationId", envelope.correlationId());
            root.put("payload", envelope.payload());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("unable to serialize EventEnvelope", exception);
        }
    }

    private void persistDeadLetter(ReliableEventEnvelope envelope, String classification, String message, int attempts) {
        recoveryTransaction.executeWithoutResult(status -> reliability.deadLetter(
                CONSUMER, envelope.eventId(), envelope.eventType(), envelope.correlationId(), serialize(envelope),
                classification, safeMessage(message), attempts, Instant.now()));
    }

    private void persistDeferred(ReliableEventEnvelope envelope, String reason) {
        reliability.defer(CONSUMER, envelope, serialize(envelope), reason, Instant.now());
    }

    private String required(com.fasterxml.jackson.databind.JsonNode root, String field) {
        if (!root.hasNonNull(field) || root.get(field).asText().isBlank()) {
            throw new NonRetryableEventException("missing " + field);
        }
        return root.get(field).asText();
    }

    private String safeMessage(Throwable exception) {
        return safeMessage(exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }

    private String safeMessage(String value) {
        String safe = value == null ? "unknown failure" : value;
        return safe.length() <= 1024 ? safe : safe.substring(0, 1024);
    }
}
