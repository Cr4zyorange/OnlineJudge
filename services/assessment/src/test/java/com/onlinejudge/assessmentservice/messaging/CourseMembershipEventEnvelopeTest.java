package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseMembershipEventEnvelopeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyTheClosedCanonicalV2CourseMemberEnvelope() throws Exception {
        var envelope = CourseMembershipEventEnvelope.parse(json.readTree("""
                {"eventId":"2dc17378-0d59-4efe-b72a-e4a29a54c879","eventType":"course.member.changed.v2","payloadVersion":2,
                 "aggregateType":"course-member","aggregateId":"course-7:student-42","aggregateVersion":3,
                 "occurredAt":"2026-08-31T00:00:00Z","correlationId":"94d26183-6e01-4bf8-9660-b24d106bbd82",
                 "payload":{"courseId":"course-7","userId":"student-42","membershipStatus":"ACTIVE","memberVersion":3}}
                """));

        assertThat(envelope.courseId()).isEqualTo("course-7");
        assertThat(envelope.memberVersion()).isEqualTo(3);
    }

    @Test
    void rejectsV1OrMalformedPayloadBeforeItCanGrantMembership() throws Exception {
        assertThatThrownBy(() -> CourseMembershipEventEnvelope.parse(json.readTree("""
                {"eventId":"2dc17378-0d59-4efe-b72a-e4a29a54c879","eventType":"course.member.changed.v2","payloadVersion":1,
                 "aggregateType":"course-member","aggregateId":"course-7:student-42","aggregateVersion":3,
                 "occurredAt":"2026-08-31T00:00:00Z","correlationId":"94d26183-6e01-4bf8-9660-b24d106bbd82",
                 "payload":{"courseId":"course-7","userId":"student-42","membershipStatus":"ACTIVE","memberVersion":3}}
                """))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CourseMembershipEventEnvelope.parse(json.readTree("""
                {"eventId":"2dc17378-0d59-4efe-b72a-e4a29a54c879","eventType":"course.member.changed.v2","payloadVersion":2,
                 "aggregateType":"course-member","aggregateId":"course-7:other-user","aggregateVersion":3,
                 "occurredAt":"2026-08-31T00:00:00Z","correlationId":"94d26183-6e01-4bf8-9660-b24d106bbd82",
                 "payload":{"courseId":"course-7","userId":"student-42","membershipStatus":"ACTIVE","memberVersion":3,"untrusted":"no"}}
                """))).isInstanceOf(IllegalArgumentException.class);
    }
}
