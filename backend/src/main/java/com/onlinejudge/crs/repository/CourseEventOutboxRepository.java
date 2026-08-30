package com.onlinejudge.crs.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.OutboxRecord;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.crs.domain.CourseMember;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Course-owned transactional outbox for the two membership facts consumed by
 * Learning.  Callers invoke it inside the same local transaction as the
 * membership mutation; no broker client is involved on that transaction.
 */
@Repository
public class CourseEventOutboxRepository {
    public static final String MEMBER_CHANGED = "course.member.changed.v2";
    public static final String MEMBERSHIP_SNAPSHOT = "course.membership.snapshot.v2";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CourseEventOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** A newly created course has an authoritative (possibly empty) student roster. */
    public void appendBootstrapRoster(long courseId) {
        Instant occurredAt = Instant.now();
        appendRosterSnapshot(courseId, UUID.randomUUID().toString(), occurredAt, null, 0L);
    }

    /**
     * Emits an incremental member fact and a complete replacement roster for
     * the Course's current student receiver scope.  Snapshot first prevents a
     * following incremental fact from making a stale partial roster appear
     * sufficient to Learning.
     */
    public void appendMembershipMutation(CourseMember changedMember) {
        long courseId = changedMember.courseId();
        long memberVersion = nextVersion("course-member", courseId + ":" + changedMember.userId());
        Instant occurredAt = Instant.now();
        String correlationId = UUID.randomUUID().toString();
        appendRosterSnapshot(courseId, correlationId, occurredAt, changedMember.userId(), memberVersion);
        appendMemberChanged(changedMember, memberVersion, correlationId, occurredAt);
    }

    public List<OutboxRecord> claimDue(String leaseOwner, Instant now, Duration leaseDuration, int limit) {
        List<Long> candidates = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM course_event_outbox
                        WHERE (delivery_status IN ('PENDING', 'RETRY')
                               AND next_attempt_at <= ?
                               AND (lease_until IS NULL OR lease_until < ?))
                           OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?)
                        ORDER BY next_attempt_at, id
                        LIMIT ?
                        """, Long.class, now, now, now, Math.max(1, limit));
        Instant leaseUntil = now.plus(leaseDuration);
        List<Long> claimedIds = new ArrayList<>();
        for (Long id : candidates) {
            int updated = jdbcTemplate.update("""
                            UPDATE course_event_outbox
                            SET delivery_status = 'IN_FLIGHT', lease_owner = ?, lease_until = ?, updated_at = ?
                            WHERE id = ?
                              AND ((delivery_status IN ('PENDING', 'RETRY')
                                    AND next_attempt_at <= ?
                                    AND (lease_until IS NULL OR lease_until < ?))
                                   OR (delivery_status = 'IN_FLIGHT' AND lease_until < ?))
                            """, leaseOwner, leaseUntil, now, id, now, now, now);
            if (updated == 1) {
                claimedIds.add(id);
            }
        }
        if (claimedIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(claimedIds.size(), "?"));
        return jdbcTemplate.query("""
                        SELECT id, event_id, event_type, payload_version, aggregate_type, aggregate_id,
                               aggregate_version, correlation_id, payload_json, routing_key, attempt_count, next_attempt_at
                        FROM course_event_outbox
                        WHERE id IN (%s)
                        ORDER BY id
                        """.formatted(placeholders), (resultSet, rowNum) -> new OutboxRecord(
                resultSet.getLong("id"),
                deserialize(
                        resultSet.getString("event_id"), resultSet.getString("event_type"),
                        resultSet.getInt("payload_version"), resultSet.getString("aggregate_type"),
                        resultSet.getString("aggregate_id"), resultSet.getLong("aggregate_version"),
                        resultSet.getString("correlation_id"), resultSet.getString("payload_json")
                ),
                resultSet.getString("routing_key"), resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("next_attempt_at").toInstant()
        ), claimedIds.toArray());
    }

    public void markPublished(long id, String leaseOwner, Instant publishedAt) {
        jdbcTemplate.update("""
                        UPDATE course_event_outbox
                        SET delivery_status = 'PUBLISHED', published_at = ?, lease_owner = NULL, lease_until = NULL,
                            updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, publishedAt, publishedAt, id, leaseOwner, publishedAt);
    }

    public void markFailedAttempt(
            long id, String leaseOwner, int attemptCount, Instant nextAttemptAt,
            boolean terminal, String error, Instant updatedAt
    ) {
        jdbcTemplate.update("""
                        UPDATE course_event_outbox
                        SET delivery_status = ?, attempt_count = ?, next_attempt_at = ?, last_error = ?,
                            lease_owner = NULL, lease_until = NULL, updated_at = ?
                        WHERE id = ? AND delivery_status = 'IN_FLIGHT' AND lease_owner = ? AND lease_until >= ?
                        """, terminal ? "FAILED" : "RETRY", attemptCount, nextAttemptAt, error, updatedAt,
                id, leaseOwner, updatedAt);
    }

    private void appendRosterSnapshot(
            long courseId, String correlationId, Instant occurredAt, Long changedUserId, long changedUserVersion
    ) {
        long rosterVersion = nextVersion("course-membership-roster", String.valueOf(courseId));
        List<Map<String, Object>> members = jdbcTemplate.query("""
                        SELECT user_id, join_status
                        FROM crs_course_member
                        WHERE course_id = ? AND role = 'STUDENT' AND is_deleted = FALSE
                        ORDER BY user_id
                        """, (rs, rowNum) -> {
            long userId = rs.getLong("user_id");
            long memberVersion = changedUserId != null && changedUserId == userId
                    ? changedUserVersion
                    : Math.max(1L, lastVersion("course-member", courseId + ":" + userId));
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("userId", String.valueOf(userId));
            member.put("membershipStatus", normalizedStudentStatus(CourseMemberRole.STUDENT,
                    CourseMemberStatus.valueOf(rs.getString("join_status"))));
            member.put("memberVersion", memberVersion);
            return member;
        }, courseId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", String.valueOf(courseId));
        payload.put("rosterVersion", rosterVersion);
        payload.put("members", members);
        append(new ReliableEventEnvelope(
                UUID.randomUUID().toString(), MEMBERSHIP_SNAPSHOT, 2,
                "course-membership-roster", String.valueOf(courseId), rosterVersion,
                occurredAt, correlationId, objectMapper.valueToTree(payload)
        ));
    }

    private void appendMemberChanged(CourseMember member, long memberVersion, String correlationId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", String.valueOf(member.courseId()));
        payload.put("userId", String.valueOf(member.userId()));
        payload.put("membershipStatus", normalizedStudentStatus(member.role(), member.status()));
        payload.put("memberVersion", memberVersion);
        append(new ReliableEventEnvelope(
                UUID.randomUUID().toString(), MEMBER_CHANGED, 2, "course-member",
                member.courseId() + ":" + member.userId(), memberVersion,
                occurredAt, correlationId, objectMapper.valueToTree(payload)
        ));
    }

    private void append(ReliableEventEnvelope envelope) {
        jdbcTemplate.update("""
                        INSERT INTO course_event_outbox
                        (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
                         correlation_id, payload_json, routing_key, delivery_status, attempt_count,
                         next_attempt_at, lease_owner, lease_until, last_error, published_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, NULL, NULL, NULL, NULL, ?, ?)
                        """, envelope.eventId(), envelope.eventType(), envelope.payloadVersion(),
                envelope.aggregateType(), envelope.aggregateId(), envelope.aggregateVersion(),
                envelope.correlationId(), serialize(envelope), "onlinejudge." + envelope.eventType(),
                envelope.occurredAt(), envelope.occurredAt(), envelope.occurredAt());
    }

    private long nextVersion(String aggregateType, String aggregateId) {
        return lastVersion(aggregateType, aggregateId) + 1L;
    }

    private long lastVersion(String aggregateType, String aggregateId) {
        Long value = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(aggregate_version), 0)
                        FROM course_event_outbox
                        WHERE aggregate_type = ? AND aggregate_id = ?
                        """, Long.class, aggregateType, aggregateId);
        return value == null ? 0L : value;
    }

    private String normalizedStudentStatus(CourseMemberRole role, CourseMemberStatus status) {
        return role == CourseMemberRole.STUDENT && status == CourseMemberStatus.ACTIVE ? "ACTIVE" : "REMOVED";
    }

    private ReliableEventEnvelope deserialize(
            String eventId, String eventType, int payloadVersion, String aggregateType, String aggregateId,
            long aggregateVersion, String correlationId, String json
    ) {
        try {
            var node = objectMapper.readTree(json);
            ReliableEventEnvelope envelope = new ReliableEventEnvelope(
                    eventId, eventType, payloadVersion, aggregateType, aggregateId, aggregateVersion,
                    Instant.parse(node.required("occurredAt").asText()), correlationId, node.required("payload")
            );
            envelope.requireV2();
            return envelope;
        } catch (Exception exception) {
            throw new IllegalStateException("stored Course outbox envelope is invalid", exception);
        }
    }

    private String serialize(ReliableEventEnvelope envelope) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("eventId", envelope.eventId());
            root.put("eventType", envelope.eventType());
            root.put("payloadVersion", envelope.payloadVersion());
            root.put("aggregateType", envelope.aggregateType());
            root.put("aggregateId", envelope.aggregateId());
            root.put("aggregateVersion", envelope.aggregateVersion());
            root.put("occurredAt", envelope.occurredAt().toString());
            root.put("correlationId", envelope.correlationId());
            root.put("payload", envelope.payload());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize Course v2 event envelope", exception);
        }
    }
}
