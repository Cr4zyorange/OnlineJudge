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
        try {
            return projection.apply(SourceGradeChangedEnvelope.parse(json.readTree(body)));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new IllegalArgumentException("invalid assessment.source-grade.changed.v2 JSON", malformed);
        }
    }
}
