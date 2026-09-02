package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.service.SourceGradeProjectionService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceGradeRabbitMessageHandlerTest {
    private SourceGradeChangedEnvelope applied;
    private final SourceGradeProjectionService projection = new SourceGradeProjectionService(null) {
        @Override
        public ApplyResult apply(SourceGradeChangedEnvelope event) {
            applied = event;
            return new ApplyResult("APPLIED");
        }
    };
    private final SourceGradeRabbitMessageHandler handler = new SourceGradeRabbitMessageHandler(new ObjectMapper(), projection);

    @Test
    void parsesAndAppliesOnlyTheFrozenV2SourceGradeFact() {
        handler.handle(canonical().getBytes(StandardCharsets.UTF_8));

        assertThat(applied.aggregateId()).isEqualTo("LAB:71:11");
        assertThat(applied.sourceVersion()).isEqualTo(1);
        assertThatThrownBy(() -> handler.handle(canonical().replace("\"payloadVersion\":2", "\"payloadVersion\":1")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propagatesProjectionFailuresSoTheConsumerCanRequeueThemInsteadOfDeadLetteringAsInvalidJson() {
        SourceGradeProjectionService unavailableProjection = new SourceGradeProjectionService(null) {
            @Override
            public ApplyResult apply(SourceGradeChangedEnvelope event) {
                throw new IllegalStateException("temporary projection storage outage");
            }
        };
        SourceGradeRabbitMessageHandler unavailableHandler = new SourceGradeRabbitMessageHandler(new ObjectMapper(), unavailableProjection);

        assertThatThrownBy(() -> unavailableHandler.handle(canonical().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary projection storage outage");
    }

    private static String canonical() {
        return """
                {"eventId":"94409d40-b115-4ad5-bb45-5410b82a24a4","eventType":"assessment.source-grade.changed.v2","payloadVersion":2,
                 "aggregateType":"assessment-source-grade","aggregateId":"LAB:71:11","aggregateVersion":1,
                 "occurredAt":"2026-08-30T09:15:30Z","correlationId":"e2dc79b2-2c18-4dca-bc18-e8573e7d9fe5",
                 "payload":{"courseId":"41","sourceType":"LAB","sourceId":"71","studentId":"11",
                 "score":92,"fullScore":100,"status":"SCORED","sourceVersion":1}}
                """;
    }
}
