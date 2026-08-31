package com.onlinejudge.courseservice.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Durable projection of Identity's ordered security-version aggregate.  A
 * newly observed higher version is immediately fail-closed for JWTs, even if
 * an earlier event is missing; reconciliation later changes only diagnostics,
 * never lowers the stored minimum.
 */
@Repository
public class CourseEventInboxRepository {
    private static final String EVENT_TYPE = "identity.security-version.changed.v2";
    private static final String AGGREGATE_TYPE = "identity-user";
    private static final Set<String> CHANGE_REASONS = Set.of(
            "LOGOUT", "PASSWORD_CHANGED", "ROLE_CHANGED", "PERMISSION_CHANGED", "ACCOUNT_STATUS_CHANGED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CourseEventInboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ProjectionResult projectIdentitySecurityVersion(String rawEnvelope) {
        IdentitySecurityVersionEvent event = parse(rawEnvelope);
        Integer duplicate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_inbox WHERE event_id = ?", Integer.class, event.eventId());
        if (duplicate != null && duplicate > 0) {
            return new ProjectionResult(event.eventId(), "DUPLICATE", minimumSecurityVersion(Long.parseLong(event.userId())));
        }
        jdbcTemplate.update("""
                INSERT INTO event_inbox
                (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
                 correlation_id, payload_json, processing_status)
                VALUES (?, ?, 2, ?, ?, ?, ?, ?, 'DEFERRED_GAP')
                """, event.eventId(), EVENT_TYPE, AGGREGATE_TYPE, event.userId(), event.securityVersion(),
                event.correlationId(), rawEnvelope);
        reconcileIdentityAggregate(event.userId());
        String status = jdbcTemplate.queryForObject("SELECT processing_status FROM event_inbox WHERE event_id = ?", String.class, event.eventId());
        return new ProjectionResult(event.eventId(), status, minimumSecurityVersion(Long.parseLong(event.userId())));
    }

    /** Maximum observed canonical version is the fail-closed JWT floor. */
    public long minimumSecurityVersion(long userId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(aggregate_version), 0)
                  FROM event_inbox
                 WHERE event_type = ? AND aggregate_type = ? AND aggregate_id = ?
                """, Long.class, EVENT_TYPE, AGGREGATE_TYPE, String.valueOf(userId));
        return value == null ? 0 : value;
    }

    public boolean accepts(long userId, long tokenSecurityVersion) {
        return tokenSecurityVersion >= minimumSecurityVersion(userId);
    }

    /** Reclassifies durable gap rows once their missing predecessor is replayed. */
    public void reconcileIdentityAggregate(String userId) {
        List<VersionedEvent> events = jdbcTemplate.query("""
                SELECT event_id, aggregate_version
                  FROM event_inbox
                 WHERE event_type = ? AND aggregate_type = ? AND aggregate_id = ?
                 ORDER BY aggregate_version, received_at, event_id
                """, (rs, row) -> new VersionedEvent(rs.getString(1), rs.getLong(2)), EVENT_TYPE, AGGREGATE_TYPE, userId);
        if (events.isEmpty()) return;
        long expected = events.getFirst().version();
        for (VersionedEvent event : events) {
            String status;
            if (event.version() == expected) {
                status = "APPLIED";
                expected++;
            } else if (event.version() > expected) {
                status = "DEFERRED_GAP";
            } else {
                status = "IGNORED_OLD";
            }
            jdbcTemplate.update("UPDATE event_inbox SET processing_status = ?, updated_at = CURRENT_TIMESTAMP WHERE event_id = ?", status, event.eventId());
        }
    }

    private IdentitySecurityVersionEvent parse(String rawEnvelope) {
        try {
            JsonNode envelope = objectMapper.readTree(rawEnvelope);
            String eventId = requiredText(envelope, "eventId");
            if (!EVENT_TYPE.equals(requiredText(envelope, "eventType")) || envelope.path("payloadVersion").asInt(-1) != 2
                    || !AGGREGATE_TYPE.equals(requiredText(envelope, "aggregateType"))) {
                throw new IllegalArgumentException("event is not the canonical Identity security-version v2 fact");
            }
            String aggregateId = requiredText(envelope, "aggregateId");
            long aggregateVersion = positive(envelope, "aggregateVersion");
            String correlationId = requiredText(envelope, "correlationId");
            JsonNode payload = envelope.path("payload");
            String userId = requiredText(payload, "userId");
            long securityVersion = positive(payload, "securityVersion");
            String changeReason = requiredText(payload, "changeReason");
            if (!aggregateId.equals(userId) || aggregateVersion != securityVersion || !CHANGE_REASONS.contains(changeReason)) {
                throw new IllegalArgumentException("Identity security-version envelope does not match its aggregate");
            }
            Long.parseLong(userId);
            return new IdentitySecurityVersionEvent(eventId, userId, securityVersion, correlationId);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid Identity security-version v2 envelope", exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private long positive(JsonNode node, String field) {
        if (!node.path(field).canConvertToLong() || node.path(field).asLong() < 1) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return node.path(field).asLong();
    }

    private record VersionedEvent(String eventId, long version) { }
    private record IdentitySecurityVersionEvent(String eventId, String userId, long securityVersion, String correlationId) { }
    public record ProjectionResult(String eventId, String status, long minimumSecurityVersion) { }
}
