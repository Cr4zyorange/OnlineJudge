package com.onlinejudge.hwk.service;

import com.onlinejudge.common.reliability.ConfirmedEventPublisher;
import com.onlinejudge.common.reliability.OutboxRecord;
import com.onlinejudge.hwk.repository.AssessmentEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lease based publisher for the Assessment-owned outbox.  A failed broker call
 * changes only delivery metadata; the previously committed homework fact is
 * never rolled back.
 */
@Component
@ConditionalOnProperty(name = "onlinejudge.reliability.publisher.enabled", havingValue = "true")
public class AssessmentOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(AssessmentOutboxPublisher.class);

    private final AssessmentEventOutboxRepository repository;
    private final ConfirmedEventPublisher publisher;
    private final String leaseOwner = "assessment-publisher-" + UUID.randomUUID();
    private final int batchSize;
    private final int maxAttempts;
    private final Duration leaseDuration;
    private final Duration retryBase;
    private final Duration retryMaximum;

    public AssessmentOutboxPublisher(
            AssessmentEventOutboxRepository repository,
            ConfirmedEventPublisher publisher,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.publisher.batch-size:50}") int batchSize,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.publisher.max-attempts:8}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.publisher.lease-seconds:30}") long leaseSeconds,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.publisher.retry-base-seconds:1}") long retryBaseSeconds,
            @org.springframework.beans.factory.annotation.Value("${onlinejudge.reliability.publisher.retry-max-seconds:300}") long retryMaxSeconds
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseDuration = Duration.ofSeconds(Math.max(1, leaseSeconds));
        this.retryBase = Duration.ofSeconds(Math.max(1, retryBaseSeconds));
        this.retryMaximum = Duration.ofSeconds(Math.max(retryBase.getSeconds(), retryMaxSeconds));
    }

    @Scheduled(fixedDelayString = "${onlinejudge.reliability.publisher.fixed-delay-ms:5000}")
    public void publishDueMessages() {
        drain(Instant.now());
    }

    public int drain(Instant now) {
        List<OutboxRecord> claimed = repository.claimDue(leaseOwner, now, leaseDuration, batchSize);
        for (OutboxRecord record : claimed) {
            try {
                publisher.publish(record.envelope(), record.routingKey());
                repository.markPublished(record.id(), leaseOwner, Instant.now());
            } catch (RuntimeException exception) {
                int nextAttempt = record.attemptCount() + 1;
                boolean terminal = nextAttempt >= maxAttempts;
                repository.markFailedAttempt(
                        record.id(),
                        leaseOwner,
                        nextAttempt,
                        terminal ? Instant.now() : now.plus(backoff(nextAttempt)),
                        terminal,
                        safeError(exception),
                        Instant.now()
                );
                log.warn("Assessment outbox publish failed eventId={} correlationId={} attempt={} terminal={} error={}",
                        record.envelope().eventId(), record.envelope().correlationId(), nextAttempt, terminal,
                        safeError(exception));
            }
        }
        return claimed.size();
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1));
        long seconds = Math.min(retryMaximum.getSeconds(), retryBase.getSeconds() * multiplier);
        return Duration.ofSeconds(seconds);
    }

    private String safeError(RuntimeException exception) {
        String text = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return text.length() <= 1024 ? text : text.substring(0, 1024);
    }
}
