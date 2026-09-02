package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceGradeChangedEnvelopeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsTheCanonicalScoredFixture() throws Exception {
        var envelope = SourceGradeChangedEnvelope.parse(json.readTree("""
                {"eventId":"94409d40-b115-4ad5-bb45-5410b82a24a4","eventType":"assessment.source-grade.changed.v2","payloadVersion":2,
                 "aggregateType":"assessment-source-grade","aggregateId":"HWK:homework-91:student-42","aggregateVersion":7,
                 "occurredAt":"2026-08-30T09:15:30Z","correlationId":"e2dc79b2-2c18-4dca-bc18-e8573e7d9fe5",
                 "payload":{"courseId":"course-88","sourceType":"HWK","sourceId":"homework-91","studentId":"student-42",
                 "score":92,"fullScore":100,"status":"SCORED","sourceVersion":7}}
                """));

        assertThat(envelope.sourceVersion()).isEqualTo(7);
        assertThat(envelope.score()).isEqualByComparingTo("92");
    }

    @Test
    void rejectsMismatchedAggregateIdentityRevisionAndConditionalScore() throws Exception {
        String canonical = """
                {"eventId":"94409d40-b115-4ad5-bb45-5410b82a24a4","eventType":"assessment.source-grade.changed.v2","payloadVersion":2,
                 "aggregateType":"assessment-source-grade","aggregateId":"HWK:homework-91:student-42","aggregateVersion":7,
                 "occurredAt":"2026-08-30T09:15:30Z","correlationId":"e2dc79b2-2c18-4dca-bc18-e8573e7d9fe5",
                 "payload":{"courseId":"course-88","sourceType":"HWK","sourceId":"homework-91","studentId":"student-42",
                 "score":92,"fullScore":100,"status":"SCORED","sourceVersion":7}}
                """;

        assertThatThrownBy(() -> SourceGradeChangedEnvelope.parse(json.readTree(canonical.replace("student-42\",\"aggregateVersion", "other\",\"aggregateVersion"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceGradeChangedEnvelope.parse(json.readTree(canonical.replace("\"sourceVersion\":7", "\"sourceVersion\":6"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceGradeChangedEnvelope.parse(json.readTree(canonical.replace("\"score\":92", "\"score\":null"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
