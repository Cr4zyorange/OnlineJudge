package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.service.SourceGradeProjectionService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SourceGradeRabbitMessageHandlerTest {
    private final SourceGradeProjectionService projection = mock(SourceGradeProjectionService.class);
    private final SourceGradeRabbitMessageHandler handler = new SourceGradeRabbitMessageHandler(new ObjectMapper(), projection);

    @Test
    void parsesAndAppliesOnlyTheFrozenV2SourceGradeFact() {
        handler.handle(canonical().getBytes(StandardCharsets.UTF_8));

        verify(projection).apply(argThat(event -> event.aggregateId().equals("LAB:71:11") && event.sourceVersion() == 1));
        assertThatThrownBy(() -> handler.handle(canonical().replace("\"payloadVersion\":2", "\"payloadVersion\":1")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
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
