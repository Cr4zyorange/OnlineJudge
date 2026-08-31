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
        lockCourse(event.courseId());
        long current = currentVersion(event.courseId(), event.userId());
        if (event.memberVersion() > current + 1) {
            defer(event, current + 1);
            return new ApplyResult("GAP");
        }
        if (event.memberVersion() <= current) { record(event.eventId(), "course.member.changed.v2"); return new ApplyResult("STALE"); }
        applyInOrder(event);
        drainDeferred(event.courseId(), event.userId(), event.memberVersion());
        return new ApplyResult("APPLIED");
    }
    /** A higher complete roster is an authoritative fast-forward, not a member-event gap. */
    @Transactional
    public ApplyResult applySnapshot(RosterSnapshot snapshot) {
        Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_event_inbox WHERE event_id=?", Integer.class, snapshot.eventId());
        if (seen != null && seen > 0) return new ApplyResult("DUPLICATE");
        long currentWatermark = lockCourse(snapshot.courseId());
        if (snapshot.rosterVersion() <= currentWatermark) {
            record(snapshot.eventId(), "course.membership.snapshot.v2");
            return new ApplyResult("STALE");
        }
        // The roster is complete: replace all local entries as one transaction, then only
        // drain individually newer deferred facts using the member versions in the snapshot.
        jdbc.update("DELETE FROM assessment_course_member_projection WHERE course_id=?", snapshot.courseId());
        for (RosterMember member : snapshot.members()) {
            jdbc.update("INSERT INTO assessment_course_member_projection (course_id, user_id, membership_status, member_version) VALUES (?, ?, ?, ?)",
                    snapshot.courseId(), member.userId(), member.membershipStatus(), member.memberVersion());
            jdbc.update("DELETE FROM assessment_deferred_course_member_event WHERE course_id=? AND user_id=? AND member_version <= ?", snapshot.courseId(), member.userId(), member.memberVersion());
            jdbc.update("DELETE FROM assessment_course_projection_gap WHERE course_id=? AND user_id=? AND expected_version <= ?", snapshot.courseId(), member.userId(), member.memberVersion());
        }
        jdbc.update("UPDATE assessment_course_membership_watermark SET roster_version=? WHERE course_id=?", snapshot.rosterVersion(), snapshot.courseId());
        record(snapshot.eventId(), "course.membership.snapshot.v2");
        for (RosterMember member : snapshot.members()) drainDeferred(snapshot.courseId(), member.userId(), member.memberVersion());
        return new ApplyResult("APPLIED");
    }
    /** Serializes per-member increments with roster replacement without cross-schema reads. */
    private long lockCourse(String courseId) {
        jdbc.update("INSERT INTO assessment_course_membership_watermark (course_id, roster_version) VALUES (?, 0) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id)", courseId);
        return jdbc.queryForObject("SELECT roster_version FROM assessment_course_membership_watermark WHERE course_id=? FOR UPDATE", Long.class, courseId);
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
        record(event.eventId(), "course.member.changed.v2");
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
    private void record(String id, String type) { jdbc.update("INSERT INTO assessment_event_inbox (event_id, event_type) VALUES (?, ?) ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)", id, type); }
    public record MemberChanged(String eventId, String courseId, String userId, String membershipStatus, long memberVersion) { }
    public record RosterMember(String userId, String membershipStatus, long memberVersion) { }
    public record RosterSnapshot(String eventId, String courseId, long rosterVersion, java.util.List<RosterMember> members) { }
    public record ApplyResult(String decision) { }
}
