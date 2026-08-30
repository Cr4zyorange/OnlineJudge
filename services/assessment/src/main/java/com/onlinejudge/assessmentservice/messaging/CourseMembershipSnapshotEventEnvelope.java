package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Closed representation of Course's authoritative roster bootstrap/reconciliation event. */
record CourseMembershipSnapshotEventEnvelope(String eventId, String courseId, long rosterVersion, List<Member> members) {
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "eventId", "eventType", "payloadVersion", "aggregateType", "aggregateId", "aggregateVersion", "occurredAt", "correlationId", "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("courseId", "rosterVersion", "members");
    private static final Set<String> MEMBER_FIELDS = Set.of("userId", "membershipStatus", "memberVersion");

    static CourseMembershipSnapshotEventEnvelope parse(JsonNode root) {
        require(root != null && root.isObject(), "event envelope must be an object");
        requireClosed(root, ENVELOPE_FIELDS, "event envelope");
        requireText(root, "eventId"); requireUuid(root.path("eventId").asText(), "eventId");
        require("course.membership.snapshot.v2".equals(root.path("eventType").asText()), "unexpected eventType");
        require(root.path("payloadVersion").canConvertToInt() && root.path("payloadVersion").asInt() == 2, "payloadVersion must be 2");
        require("course-membership-roster".equals(root.path("aggregateType").asText()), "unexpected aggregateType");
        require(root.path("aggregateVersion").canConvertToLong() && root.path("aggregateVersion").asLong() >= 1, "aggregateVersion must be positive");
        requireText(root, "occurredAt"); try { Instant.parse(root.path("occurredAt").asText()); } catch (RuntimeException malformed) { throw invalid("occurredAt must be RFC3339"); }
        requireText(root, "correlationId"); requireUuid(root.path("correlationId").asText(), "correlationId");

        JsonNode payload = root.path("payload");
        require(payload.isObject(), "payload must be an object"); requireClosed(payload, PAYLOAD_FIELDS, "course membership snapshot payload");
        requireText(payload, "courseId");
        require(payload.path("rosterVersion").canConvertToLong() && payload.path("rosterVersion").asLong() >= 1, "rosterVersion must be positive");
        long rosterVersion = payload.path("rosterVersion").asLong();
        require(root.path("aggregateVersion").asLong() == rosterVersion, "aggregateVersion must equal rosterVersion");
        String courseId = payload.path("courseId").asText();
        require(courseId.equals(root.path("aggregateId").asText()), "aggregateId does not match courseId");
        require(payload.path("members").isArray(), "members must be an array");
        Set<String> userIds = new HashSet<>();
        List<Member> members = new java.util.ArrayList<>();
        for (JsonNode member : payload.path("members")) {
            require(member.isObject(), "member must be an object"); requireClosed(member, MEMBER_FIELDS, "snapshot member");
            requireText(member, "userId"); String userId = member.path("userId").asText(); require(userIds.add(userId), "members must not contain duplicate userId");
            String status = member.path("membershipStatus").asText(); require("ACTIVE".equals(status) || "REMOVED".equals(status), "invalid membershipStatus");
            require(member.path("memberVersion").canConvertToLong() && member.path("memberVersion").asLong() >= 1, "memberVersion must be positive");
            members.add(new Member(userId, status, member.path("memberVersion").asLong()));
        }
        return new CourseMembershipSnapshotEventEnvelope(root.path("eventId").asText(), courseId, rosterVersion, List.copyOf(members));
    }

    record Member(String userId, String membershipStatus, long memberVersion) { }
    private static void requireClosed(JsonNode object, Set<String> fields, String name) { require(object.size() == fields.size() && object.properties().stream().allMatch(entry -> fields.contains(entry.getKey())), name + " has unknown or missing fields"); }
    private static void requireText(JsonNode node, String field) { require(node.path(field).isTextual() && !node.path(field).asText().isBlank(), field + " is required"); }
    private static void requireUuid(String value, String field) { try { UUID.fromString(value); } catch (RuntimeException malformed) { throw invalid(field + " must be UUID"); } }
    private static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("invalid course.membership.snapshot.v2 envelope: " + message); }
}
