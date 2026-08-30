package com.onlinejudge.common.reliability;

import java.time.Instant;

public record OutboxRecord(
        long id,
        ReliableEventEnvelope envelope,
        String routingKey,
        int attemptCount,
        Instant nextAttemptAt
) {
}
