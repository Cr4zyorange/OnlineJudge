package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.service.SourceGradeProjectionService;
import org.springframework.stereotype.Component;

@Component
public class SourceGradeRabbitMessageHandler {
    private final ObjectMapper json;
    private final SourceGradeProjectionService projection;

    public SourceGradeRabbitMessageHandler(ObjectMapper json, SourceGradeProjectionService projection) {
        this.json = json;
        this.projection = projection;
    }

    public SourceGradeProjectionService.ApplyResult handle(byte[] body) {
        SourceGradeChangedEnvelope event;
        try {
            event = SourceGradeChangedEnvelope.parse(json.readTree(body));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new IllegalArgumentException("invalid assessment.source-grade.changed.v2 JSON", malformed);
        }
        // Projection failures are retryable infrastructure/business failures,
        // not malformed wire facts. Let the AMQP consumer requeue them.
        return projection.apply(event);
    }
}
