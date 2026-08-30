package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentitySecurityVersionEventEnvelopeTest {
    @Test
    void acceptsOnlyTheClosedCanonicalIdentitySecurityVersionV2Envelope() throws Exception {
        String event = """
                {"eventId":"11111111-1111-4111-8111-111111111111","eventType":"identity.security-version.changed.v2","payloadVersion":2,
                 "aggregateType":"identity-user","aggregateId":"student-identity","aggregateVersion":2,"occurredAt":"2026-08-31T00:00:00Z",
                 "correlationId":"22222222-2222-4222-8222-222222222222","payload":{"userId":"student-identity","securityVersion":2,"changeReason":"ROLE_CHANGED"}}
                """;
        var parsed = IdentitySecurityVersionEventEnvelope.parse(new ObjectMapper().readTree(event));
        assertThat(parsed.userId()).isEqualTo("student-identity");
        assertThat(parsed.aggregateVersion()).isEqualTo(2);
        assertThatThrownBy(() -> IdentitySecurityVersionEventEnvelope.parse(new ObjectMapper().readTree(event.replace("\"payloadVersion\":2", "\"payloadVersion\":1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
