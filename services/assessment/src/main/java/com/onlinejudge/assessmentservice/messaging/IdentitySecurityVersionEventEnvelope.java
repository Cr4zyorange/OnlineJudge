package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Closed consumer-side view of identity.security-version.changed.v2; invalid data cannot affect revocation state. */
record IdentitySecurityVersionEventEnvelope(String eventId, String userId, long securityVersion, String changeReason, long aggregateVersion) {
    private static final Set<String> ENVELOPE_FIELDS = Set.of("eventId", "eventType", "payloadVersion", "aggregateType", "aggregateId", "aggregateVersion", "occurredAt", "correlationId", "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("userId", "securityVersion", "changeReason");
    private static final Set<String> CHANGE_REASONS = Set.of("LOGOUT", "PASSWORD_CHANGED", "ROLE_CHANGED", "PERMISSION_CHANGED", "ACCOUNT_STATUS_CHANGED");

    static IdentitySecurityVersionEventEnvelope parse(JsonNode root) {
        require(root != null && root.isObject(), "event envelope must be an object");
        requireClosed(root, ENVELOPE_FIELDS, "event envelope");
        requireText(root, "eventId"); requireUuid(root.path("eventId").asText(), "eventId");
        require("identity.security-version.changed.v2".equals(root.path("eventType").asText()), "unexpected eventType");
        require(root.path("payloadVersion").canConvertToInt() && root.path("payloadVersion").asInt() == 2, "payloadVersion must be 2");
        require("identity-user".equals(root.path("aggregateType").asText()), "unexpected aggregateType");
        require(root.path("aggregateVersion").canConvertToLong() && root.path("aggregateVersion").asLong() >= 1, "aggregateVersion must be positive");
        requireText(root, "occurredAt"); try { Instant.parse(root.path("occurredAt").asText()); } catch (RuntimeException malformed) { throw invalid("occurredAt must be RFC3339"); }
        requireText(root, "correlationId"); requireUuid(root.path("correlationId").asText(), "correlationId");
        JsonNode payload = root.path("payload"); require(payload.isObject(), "payload must be an object"); requireClosed(payload, PAYLOAD_FIELDS, "identity security version payload");
        requireText(payload, "userId");
        require(payload.path("securityVersion").canConvertToLong() && payload.path("securityVersion").asLong() >= 1, "securityVersion must be positive");
        String changeReason = payload.path("changeReason").asText(); require(CHANGE_REASONS.contains(changeReason), "invalid changeReason");
        String userId = payload.path("userId").asText(); long securityVersion = payload.path("securityVersion").asLong(); long aggregateVersion = root.path("aggregateVersion").asLong();
        require(userId.equals(root.path("aggregateId").asText()), "aggregateId does not match userId");
        require(aggregateVersion == securityVersion, "aggregateVersion must equal securityVersion");
        return new IdentitySecurityVersionEventEnvelope(root.path("eventId").asText(), userId, securityVersion, changeReason, aggregateVersion);
    }
    private static void requireClosed(JsonNode object, Set<String> fields, String name) { require(object.size() == fields.size() && object.properties().stream().allMatch(entry -> fields.contains(entry.getKey())), name + " has unknown or missing fields"); }
    private static void requireText(JsonNode node, String field) { require(node.path(field).isTextual() && !node.path(field).asText().isBlank(), field + " is required"); }
    private static void requireUuid(String value, String field) { try { UUID.fromString(value); } catch (RuntimeException malformed) { throw invalid(field + " must be UUID"); } }
    private static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("invalid identity.security-version.changed.v2 envelope: " + message); }
}
