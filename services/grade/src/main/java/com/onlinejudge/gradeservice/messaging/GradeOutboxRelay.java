package com.onlinejudge.gradeservice.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** At-least-once relay: the database fact is marked delivered only after a publisher confirm. */
@Component
@ConditionalOnProperty(name = {"grade.rabbit.enabled", "grade.rabbit.relay-enabled"}, havingValue = "true")
public class GradeOutboxRelay {
    private final GradeOutboxRepository repository;
    private final GradeOutboxTransport transport;

    public GradeOutboxRelay(GradeOutboxRepository repository, GradeOutboxTransport transport) {
        this.repository = repository;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${grade.rabbit.relay-interval-ms:1000}")
    public void publishPending() {
        for (var event : repository.pending(100)) {
            try {
                transport.publish(event);
                repository.markDelivered(event.eventId());
            } catch (Exception failure) {
                repository.recordFailure(event.eventId(), failure.getMessage());
            }
        }
    }
}
