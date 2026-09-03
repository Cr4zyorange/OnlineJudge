package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseMembershipSnapshotEventEnvelopeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyAClosedCanonicalRosterSnapshotWithAnAtomicWatermark() throws Exception {
        var snapshot = CourseMembershipSnapshotEventEnvelope.parse(json.readTree("""
                {"eventId":"6f5bd527-8d0f-4c1b-8f6f-2a8a997ce218","eventType":"course.membership.snapshot.v2","payloadVersion":2,
                 "aggregateType":"course-membership-roster","aggregateId":"course-88","aggregateVersion":7,
                 "occurredAt":"2026-08-31T09:15:30Z","correlationId":"c4baece2-8d5f-46a3-9a4d-0c9540ee7d63",
                 "payload":{"courseId":"course-88","rosterVersion":7,"members":[{"userId":"user-10","membershipStatus":"ACTIVE","memberVersion":3}]}}
                """));

        assertThat(snapshot.courseId()).isEqualTo("course-88");
        assertThat(snapshot.rosterVersion()).isEqualTo(7);
        assertThat(snapshot.members()).singleElement().extracting(CourseMembershipSnapshotEventEnvelope.Member::userId).isEqualTo("user-10");
        assertThatThrownBy(() -> CourseMembershipSnapshotEventEnvelope.parse(json.readTree("""
                {"eventId":"6f5bd527-8d0f-4c1b-8f6f-2a8a997ce218","eventType":"course.membership.snapshot.v2","payloadVersion":2,
                 "aggregateType":"course-membership-roster","aggregateId":"course-88","aggregateVersion":7,
                 "occurredAt":"2026-08-31T09:15:30Z","correlationId":"c4baece2-8d5f-46a3-9a4d-0c9540ee7d63",
                 "payload":{"courseId":"course-88","rosterVersion":7,"members":[{"userId":"user-10","membershipStatus":"ACTIVE","memberVersion":3},{"userId":"user-10","membershipStatus":"REMOVED","memberVersion":4}]}}
                """))).isInstanceOf(IllegalArgumentException.class);
    }
}
