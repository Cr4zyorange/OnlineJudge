package com.onlinejudge.gradeservice.messaging;

@FunctionalInterface
public interface GradeOutboxTransport {
    void publish(GradeOutboxRepository.OutboxEvent event) throws Exception;
}
