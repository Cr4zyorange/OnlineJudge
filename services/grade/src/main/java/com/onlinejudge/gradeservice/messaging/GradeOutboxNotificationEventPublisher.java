package com.onlinejudge.gradeservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Converts the reviewed legacy GRD callback boundary into canonical v2 Grade outbox facts. */
@Component
public class GradeOutboxNotificationEventPublisher implements NotificationEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public GradeOutboxNotificationEventPublisher(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void publish(NotificationEvent notification) {
        CanonicalFact fact = canonicalFact(notification);
        if (fact == null) return;
        try {
            String eventId = UUID.nameUUIDFromBytes(("grade-event:" + notification.idempotencyKey())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            String correlationId = UUID.nameUUIDFromBytes(("grade-correlation:" + notification.idempotencyKey())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            Instant occurredAt = notification.occurredAt().toInstant(ZoneOffset.UTC);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", eventId);
            envelope.put("eventType", fact.eventType());
            envelope.put("payloadVersion", 2);
            envelope.put("aggregateType", fact.aggregateType());
            envelope.put("aggregateId", fact.aggregateId());
            envelope.put("aggregateVersion", fact.aggregateVersion());
            envelope.put("occurredAt", occurredAt.toString());
            envelope.put("correlationId", correlationId);
            envelope.put("payload", fact.payload());
            jdbc.update("""
                    INSERT INTO grade_event_outbox
                        (event_id, idempotency_key, event_type, payload_version, aggregate_type, aggregate_id,
                         aggregate_version, occurred_at, correlation_id, payload_json, delivery_status,
                         delivery_attempt, next_attempt_at, created_at)
                    VALUES (?, ?, ?, 2, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    ON DUPLICATE KEY UPDATE idempotency_key=VALUES(idempotency_key)
                    """, eventId, notification.idempotencyKey(), fact.eventType(), fact.aggregateType(),
                    fact.aggregateId(), fact.aggregateVersion(), Timestamp.from(occurredAt), correlationId,
                    json.writeValueAsString(envelope), Timestamp.from(occurredAt), Timestamp.from(occurredAt));
        } catch (Exception serializationOrWriteFailure) {
            throw new IllegalStateException("grade outbox fact could not be persisted", serializationOrWriteFailure);
        }
    }

    private CanonicalFact canonicalFact(NotificationEvent notification) {
        if ("GRADE_PUBLISHED".equals(notification.type()) || "GRADE_CHANGED".equals(notification.type())) {
            String publicationId = String.valueOf(notification.targetId());
            Instant publishedAt = notification.occurredAt().toInstant(ZoneOffset.UTC);
            String aggregateType = "GRADE_CHANGED".equals(notification.type())
                    ? "course-grade-summary" : "grade-publication";
            return new CanonicalFact("grade.published.v2", aggregateType, publicationId, 1,
                    Map.of("courseId", String.valueOf(notification.courseId()),
                            "publicationId", publicationId,
                            "publishedAt", publishedAt.toString(),
                            "publicationVersion", 1));
        }
        if ("GRADE_REVIEW_PROCESSED".equals(notification.type())) {
            String reviewId = String.valueOf(notification.targetId());
            ReviewFact review = jdbc.query("""
                    SELECT student_id, status, processed_at FROM t_grade_review_request WHERE id=?
                    """, (rs, ignored) -> new ReviewFact(rs.getLong("student_id"), rs.getString("status"),
                    rs.getTimestamp("processed_at").toInstant()), notification.targetId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("processed review does not exist"));
            String status = review.status();
            if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
                throw new IllegalStateException("processed review must be APPROVED or REJECTED");
            }
            return new CanonicalFact("grade.review.processed.v2", "grade-review", reviewId, 1,
                    Map.of("courseId", String.valueOf(notification.courseId()),
                            "reviewRequestId", reviewId,
                            "studentId", String.valueOf(review.studentId()),
                            "reviewStatus", status,
                            "resultVersion", 1,
                            "processedAt", review.processedAt().toString()));
        }
        // The frozen three-service contract does not define review.requested facts.
        return null;
    }

    private record CanonicalFact(String eventType, String aggregateType, String aggregateId,
                                 long aggregateVersion, Map<String, Object> payload) { }
    private record ReviewFact(long studentId, String status, Instant processedAt) { }
}
