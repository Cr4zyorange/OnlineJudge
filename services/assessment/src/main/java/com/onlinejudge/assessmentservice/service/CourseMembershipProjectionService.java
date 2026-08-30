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
        Long current = jdbc.query("SELECT member_version FROM assessment_course_member_projection WHERE course_id=? AND user_id=?", (rs, ignored) -> rs.getLong(1), event.courseId(), event.userId()).stream().findFirst().orElse(0L);
        if (event.memberVersion() > current + 1) {
            jdbc.update("MERGE INTO assessment_course_projection_gap (course_id, user_id, expected_version, observed_version) KEY(course_id, user_id) VALUES (?, ?, ?, ?)", event.courseId(), event.userId(), current + 1, event.memberVersion());
            return new ApplyResult("GAP");
        }
        if (event.memberVersion() <= current) { record(event.eventId()); return new ApplyResult("STALE"); }
        int updated = jdbc.update("UPDATE assessment_course_member_projection SET membership_status=?, member_version=? WHERE course_id=? AND user_id=?", event.membershipStatus(), event.memberVersion(), event.courseId(), event.userId());
        if (updated == 0) jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, ?, ?)", event.courseId(), event.userId(), event.membershipStatus(), event.memberVersion());
        jdbc.update("DELETE FROM assessment_course_projection_gap WHERE course_id=? AND user_id=? AND expected_version <= ?", event.courseId(), event.userId(), event.memberVersion());
        record(event.eventId());
        return new ApplyResult("APPLIED");
    }
    private void record(String id) { jdbc.update("INSERT INTO assessment_event_inbox (event_id, event_type) VALUES (?, 'course.member.changed.v2')", id); }
    public record MemberChanged(String eventId, String courseId, String userId, String membershipStatus, long memberVersion) { }
    public record ApplyResult(String decision) { }
}
