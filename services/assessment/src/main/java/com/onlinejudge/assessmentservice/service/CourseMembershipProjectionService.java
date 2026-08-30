package com.onlinejudge.assessmentservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once Course projection: a gap is durable but its event id stays replayable. */
@Service
public class CourseMembershipProjectionService {
    private final JdbcTemplate jdbc;
    public CourseMembershipProjectionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public ApplyResult apply(MemberChanged event) {
        Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_inbox WHERE event_id=?", Integer.class, event.eventId());
        if (seen != null && seen > 0) return new ApplyResult("DUPLICATE");
        long current = currentVersion(event.courseId(), event.userId());
        if (event.memberVersion() > current + 1) {
            defer(event, current + 1);
            return new ApplyResult("GAP");
        }
        if (event.memberVersion() <= current) { record(event.eventId()); return new ApplyResult("STALE"); }
        applyInOrder(event);
        drainDeferred(event.courseId(), event.userId(), event.memberVersion());
        return new ApplyResult("APPLIED");
    }
    private long currentVersion(String courseId, String userId) {
        return jdbc.query("SELECT member_version FROM assessment_course_member_projection WHERE course_id=? AND user_id=?", (rs, ignored) -> rs.getLong(1), courseId, userId).stream().findFirst().orElse(0L);
    }
    private void defer(MemberChanged event, long expectedVersion) {
        jdbc.update("INSERT INTO assessment_deferred_course_member_event (event_id, course_id, user_id, membership_status, member_version) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)", event.eventId(), event.courseId(), event.userId(), event.membershipStatus(), event.memberVersion());
        jdbc.update("INSERT INTO assessment_course_projection_gap (course_id, user_id, expected_version, observed_version) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expected_version=VALUES(expected_version), observed_version=VALUES(observed_version)", event.courseId(), event.userId(), expectedVersion, event.memberVersion());
    }
    private void applyInOrder(MemberChanged event) {
        int updated = jdbc.update("UPDATE assessment_course_member_projection SET membership_status=?, member_version=? WHERE course_id=? AND user_id=?", event.membershipStatus(), event.memberVersion(), event.courseId(), event.userId());
        if (updated == 0) jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, ?, ?)", event.courseId(), event.userId(), event.membershipStatus(), event.memberVersion());
        jdbc.update("DELETE FROM assessment_course_projection_gap WHERE course_id=? AND user_id=? AND expected_version <= ?", event.courseId(), event.userId(), event.memberVersion());
        record(event.eventId());
    }
    private void drainDeferred(String courseId, String userId, long current) {
        long nextVersion = current + 1;
        while (true) {
            var deferred = jdbc.query("SELECT event_id, membership_status, member_version FROM assessment_deferred_course_member_event WHERE course_id=? AND user_id=? AND member_version=?", (rs, ignored) -> new MemberChanged(rs.getString("event_id"), courseId, userId, rs.getString("membership_status"), rs.getLong("member_version")), courseId, userId, nextVersion).stream().findFirst();
            if (deferred.isEmpty()) return;
            var next = deferred.orElseThrow();
            applyInOrder(next);
            jdbc.update("DELETE FROM assessment_deferred_course_member_event WHERE event_id=?", next.eventId());
            nextVersion++;
        }
    }
    private void record(String id) { jdbc.update("INSERT INTO assessment_event_inbox (event_id, event_type) VALUES (?, 'course.member.changed.v2') ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)", id); }
    public record MemberChanged(String eventId, String courseId, String userId, String membershipStatus, long memberVersion) { }
    public record ApplyResult(String decision) { }
}
