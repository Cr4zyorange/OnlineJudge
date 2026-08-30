package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Closed, consumer-side representation of the canonical course.member.changed.v2 wire contract. */
record CourseMembershipEventEnvelope(String eventId, String courseId, String userId, String membershipStatus, long memberVersion) {
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId", "eventType", "payloadVersion", "aggregateType", "aggregateId", "aggregateVersion", "occurredAt", "correlationId", "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("courseId", "userId", "membershipStatus", "memberVersion");

    static CourseMembershipEventEnvelope parse(JsonNode root) {
        require(root != null && root.isObject(), "event envelope must be an object");
        requireClosed(root, ENVELOPE_FIELDS, "event envelope");
        requireText(root, "eventId");
        requireUuid(root.path("eventId").asText(), "eventId");
        require("course.member.changed.v2".equals(root.path("eventType").asText()), "unexpected eventType");
        require(root.path("payloadVersion").canConvertToInt() && root.path("payloadVersion").asInt() == 2, "payloadVersion must be 2");
        require("course-member".equals(root.path("aggregateType").asText()), "unexpected aggregateType");
        require(root.path("aggregateVersion").canConvertToLong() && root.path("aggregateVersion").asLong() >= 1, "aggregateVersion must be positive");
        requireText(root, "occurredAt");
        try { Instant.parse(root.path("occurredAt").asText()); } catch (RuntimeException malformed) { throw invalid("occurredAt must be RFC3339"); }
        requireText(root, "correlationId");
        requireUuid(root.path("correlationId").asText(), "correlationId");

        JsonNode payload = root.path("payload");
        require(payload.isObject(), "payload must be an object");
        requireClosed(payload, PAYLOAD_FIELDS, "course member payload");
        requireText(payload, "courseId");
        requireText(payload, "userId");
        String membershipStatus = payload.path("membershipStatus").asText();
        require("ACTIVE".equals(membershipStatus) || "REMOVED".equals(membershipStatus), "invalid membershipStatus");
        require(payload.path("memberVersion").canConvertToLong() && payload.path("memberVersion").asLong() >= 1, "memberVersion must be positive");
        long memberVersion = payload.path("memberVersion").asLong();
        require(root.path("aggregateVersion").asLong() == memberVersion, "aggregateVersion must equal memberVersion");
        String courseId = payload.path("courseId").asText();
        String userId = payload.path("userId").asText();
        require((courseId + ":" + userId).equals(root.path("aggregateId").asText()), "aggregateId does not match course member");
        return new CourseMembershipEventEnvelope(root.path("eventId").asText(), courseId, userId, membershipStatus, memberVersion);
    }

    private static void requireClosed(JsonNode object, Set<String> fields, String name) {
        require(object.size() == fields.size() && object.properties().stream().allMatch(entry -> fields.contains(entry.getKey())), name + " has unknown or missing fields");
    }
    private static void requireText(JsonNode node, String field) { require(node.path(field).isTextual() && !node.path(field).asText().isBlank(), field + " is required"); }
    private static void requireUuid(String value, String field) { try { UUID.fromString(value); } catch (RuntimeException malformed) { throw invalid(field + " must be UUID"); } }
    private static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("invalid course.member.changed.v2 envelope: " + message); }
}
