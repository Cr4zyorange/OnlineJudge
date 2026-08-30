package com.onlinejudge.common.reliability;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** The runtime representation of the closed v2 EventEnvelope contract. */
public record ReliableEventEnvelope(
        String eventId,
        String eventType,
        int payloadVersion,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String correlationId,
        JsonNode payload
) {
    public void requireV2() {
        if (eventId == null || eventId.isBlank()
                || eventType == null || !eventType.endsWith(".v2")
                || payloadVersion != 2
                || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank()
                || aggregateVersion < 1
                || occurredAt == null
                || correlationId == null || correlationId.isBlank()
                || payload == null || !payload.isObject() || payload.isEmpty()) {
            throw new IllegalArgumentException("invalid v2 EventEnvelope");
        }
    }
}
