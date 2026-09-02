package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Closed consumer-side representation of assessment.source-grade.changed.v2. */
public record SourceGradeChangedEnvelope(
        String eventId,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String correlationId,
        String courseId,
        String sourceType,
        String sourceId,
        String studentId,
        BigDecimal score,
        BigDecimal fullScore,
        String status,
        long sourceVersion) {
    public static final String EVENT_TYPE = "assessment.source-grade.changed.v2";
    public static final String AGGREGATE_TYPE = "assessment-source-grade";
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId", "eventType", "payloadVersion", "aggregateType", "aggregateId",
            "aggregateVersion", "occurredAt", "correlationId", "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "courseId", "sourceType", "sourceId", "studentId", "score", "fullScore", "status", "sourceVersion");

    public static SourceGradeChangedEnvelope parse(JsonNode root) {
        require(root != null && root.isObject(), "event envelope must be an object");
        requireClosed(root, ENVELOPE_FIELDS, "event envelope");
        String eventId = requiredText(root, "eventId");
        requireUuid(eventId, "eventId");
        require(EVENT_TYPE.equals(root.path("eventType").asText()), "unexpected eventType");
        require(root.path("payloadVersion").canConvertToInt() && root.path("payloadVersion").asInt() == 2,
                "payloadVersion must be 2");
        require(AGGREGATE_TYPE.equals(root.path("aggregateType").asText()), "unexpected aggregateType");
        require(root.path("aggregateVersion").canConvertToLong() && root.path("aggregateVersion").asLong() >= 1,
                "aggregateVersion must be positive");
        long aggregateVersion = root.path("aggregateVersion").asLong();
        Instant occurredAt = parseInstant(requiredText(root, "occurredAt"), "occurredAt");
        String correlationId = requiredText(root, "correlationId");
        requireUuid(correlationId, "correlationId");

        JsonNode payload = root.path("payload");
        require(payload.isObject(), "payload must be an object");
        requireClosed(payload, PAYLOAD_FIELDS, "source grade payload");
        String courseId = requiredText(payload, "courseId");
        String sourceType = requiredText(payload, "sourceType");
        require("LAB".equals(sourceType) || "HWK".equals(sourceType), "sourceType must be LAB or HWK");
        String sourceId = requiredText(payload, "sourceId");
        String studentId = requiredText(payload, "studentId");
        require(payload.path("fullScore").isNumber(), "fullScore must be numeric");
        BigDecimal fullScore = payload.path("fullScore").decimalValue();
        require(fullScore.signum() > 0, "fullScore must be positive");
        String status = requiredText(payload, "status");
        JsonNode scoreNode = payload.path("score");
        BigDecimal score;
        if ("SCORED".equals(status)) {
            require(scoreNode.isNumber(), "SCORED requires a numeric score");
            score = scoreNode.decimalValue();
            require(score.signum() >= 0 && score.compareTo(fullScore) <= 0,
                    "score must be between zero and fullScore");
        } else if ("UNGRADED".equals(status)) {
            require(scoreNode.isNull(), "UNGRADED requires a null score");
            score = null;
        } else {
            throw invalid("status must be SCORED or UNGRADED");
        }
        require(payload.path("sourceVersion").canConvertToLong() && payload.path("sourceVersion").asLong() >= 1,
                "sourceVersion must be positive");
        long sourceVersion = payload.path("sourceVersion").asLong();
        require(sourceVersion == aggregateVersion, "aggregateVersion must equal sourceVersion");
        String aggregateId = sourceType + ":" + sourceId + ":" + studentId;
        require(aggregateId.equals(root.path("aggregateId").asText()), "aggregateId does not match source grade");

        return new SourceGradeChangedEnvelope(eventId, aggregateId, aggregateVersion, occurredAt, correlationId,
                courseId, sourceType, sourceId, studentId, score, fullScore, status, sourceVersion);
    }

    private static void requireClosed(JsonNode object, Set<String> fields, String name) {
        require(object.size() == fields.size()
                && object.properties().stream().allMatch(entry -> fields.contains(entry.getKey())),
                name + " has unknown or missing fields");
    }

    private static String requiredText(JsonNode node, String field) {
        require(node.path(field).isTextual() && !node.path(field).asText().isBlank(), field + " is required");
        return node.path(field).asText();
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException malformed) {
            throw invalid(field + " must be RFC3339");
        }
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException malformed) {
            throw invalid(field + " must be UUID");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("invalid assessment.source-grade.changed.v2 envelope: " + message);
    }
}
