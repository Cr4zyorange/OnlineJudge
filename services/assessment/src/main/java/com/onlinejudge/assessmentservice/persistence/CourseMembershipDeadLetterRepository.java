package com.onlinejudge.assessmentservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** Assessment-owned audit trail for terminal Course member messages and controlled replay. */
@Repository
public class CourseMembershipDeadLetterRepository {
    private final JdbcTemplate jdbc;
    public CourseMembershipDeadLetterRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void capture(String eventId, String payloadJson, String reason, Instant receivedAt) {
        jdbc.update("INSERT INTO assessment_course_member_dead_letter (event_id, payload_json, failure_reason, received_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE payload_json=VALUES(payload_json), failure_reason=VALUES(failure_reason)",
                eventId, payloadJson, reason.substring(0, Math.min(reason.length(), 256)), Timestamp.from(receivedAt));
    }
    public Optional<DeadLetter> find(String eventId) {
        return jdbc.query("SELECT event_id, payload_json, replay_count FROM assessment_course_member_dead_letter WHERE event_id=?", (rs, ignored) -> new DeadLetter(rs.getString(1), rs.getString(2), rs.getInt(3)), eventId).stream().findFirst();
    }
    public boolean markReplayed(String eventId, Instant now) {
        return jdbc.update("UPDATE assessment_course_member_dead_letter SET replay_count=replay_count+1, replayed_at=? WHERE event_id=?", Timestamp.from(now), eventId) == 1;
    }
    public record DeadLetter(String eventId, String payloadJson, int replayCount) { }
}
