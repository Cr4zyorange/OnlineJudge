package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.onlinejudge.gradeservice.service.SourceGradeProjectionService;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SourceGradeRabbitMessageHandler {
    private final ObjectMapper json;
    private final SourceGradeProjectionService projection;

    public SourceGradeRabbitMessageHandler(ObjectMapper json, SourceGradeProjectionService projection) {
        this.json = json;
        this.projection = projection;
    }

    public SourceGradeProjectionService.ApplyResult handle(byte[] body) {
        var root = parse(body);
        // Projection failures are retryable infrastructure/business failures,
        // not malformed wire facts. Let the AMQP consumer requeue them.
        return projection.apply(SourceGradeChangedEnvelope.parse(root));
    }

    private JsonNode parse(byte[] body) {
        try {
            return json.readTree(body);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("invalid assessment.source-grade.changed.v2 JSON", malformed);
        }
    }
}
