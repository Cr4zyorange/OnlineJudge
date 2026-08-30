package com.onlinejudge.courseservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public long createCourse(String name, long teacherId, String enrollmentMode) {
        jdbcTemplate.update("INSERT INTO crs_course (name, teacher_id, enrollment_mode, status, roster_version) VALUES (?, ?, ?, 'ACTIVE', 0)",
                name, teacherId, enrollmentMode);
        return jdbcTemplate.queryForObject("SELECT id FROM crs_course WHERE teacher_id = ? ORDER BY id DESC LIMIT 1", Long.class, teacherId);
    }

    public Optional<Course> findCourse(long courseId) {
        return jdbcTemplate.query("SELECT id, name, teacher_id, enrollment_mode, status, roster_version FROM crs_course WHERE id = ?",
                this::oneCourse, courseId);
    }

    public List<Long> allCourseIds() {
        return jdbcTemplate.queryForList("SELECT id FROM crs_course ORDER BY id", Long.class);
    }

    public Member insertMember(long courseId, long userId, String role, String status) {
        jdbcTemplate.update("INSERT INTO crs_course_member (course_id, user_id, role, status, member_version) VALUES (?, ?, ?, ?, 1)",
                courseId, userId, role, status);
        return member(courseId, userId).orElseThrow();
    }

    public Optional<Member> member(long courseId, long userId) {
        return jdbcTemplate.query("SELECT course_id, user_id, role, status, member_version FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                this::oneMember, courseId, userId);
    }

    public List<Member> members(long courseId) {
        return jdbcTemplate.query("SELECT course_id, user_id, role, status, member_version FROM crs_course_member WHERE course_id = ? ORDER BY user_id",
                (rs, row) -> member(rs), courseId);
    }

    public List<Member> members(long courseId, String role, int page, int size) {
        if (role == null || role.isBlank()) {
            return jdbcTemplate.query("SELECT course_id, user_id, role, status, member_version FROM crs_course_member WHERE course_id = ? AND status = 'ACTIVE' ORDER BY user_id LIMIT ? OFFSET ?",
                    (rs, row) -> member(rs), courseId, size, page * size);
        }
        return jdbcTemplate.query("SELECT course_id, user_id, role, status, member_version FROM crs_course_member WHERE course_id = ? AND status = 'ACTIVE' AND role = ? ORDER BY user_id LIMIT ? OFFSET ?",
                (rs, row) -> member(rs), courseId, role, size, page * size);
    }

    public long activeMemberCount(long courseId, String role) {
        if (role == null || role.isBlank()) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND status = 'ACTIVE'", Long.class, courseId);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND status = 'ACTIVE' AND role = ?",
                Long.class, courseId, role);
    }

    public Member updateMember(long courseId, long userId, String role, String status) {
        jdbcTemplate.update("UPDATE crs_course_member SET role = ?, status = ?, member_version = member_version + 1, updated_at = CURRENT_TIMESTAMP WHERE course_id = ? AND user_id = ?",
                role, status, courseId, userId);
        return member(courseId, userId).orElseThrow();
    }

    public long advanceRoster(long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET roster_version = roster_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", courseId);
        return findCourse(courseId).orElseThrow().rosterVersion();
    }

    /** Legacy rows predate Course eventing; version one is their first valid canonical watermark. */
    public Course ensureCanonicalRosterVersion(long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET roster_version = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND roster_version < 1", courseId);
        return findCourse(courseId).orElseThrow();
    }

    public void createChapter(long courseId, String title, String parentId, long actorId) {
        jdbcTemplate.update("INSERT INTO crs_chapter (course_id, title, parent_id, created_by) VALUES (?, ?, ?, ?)",
                courseId, title, parentId == null || parentId.isBlank() ? null : Long.parseLong(parentId), actorId);
    }

    public List<Chapter> chapters(long courseId) {
        return jdbcTemplate.query("SELECT id, course_id, title, parent_id FROM crs_chapter WHERE course_id = ? ORDER BY id",
                (rs, row) -> new Chapter(rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), nullableLong(rs, "parent_id")), courseId);
    }

    public void createResource(long courseId, String title, String url, long actorId) {
        jdbcTemplate.update("INSERT INTO crs_resource (course_id, title, resource_url, created_by) VALUES (?, ?, ?, ?)",
                courseId, title, url, actorId);
    }

    public List<Resource> resources(long courseId) {
        return jdbcTemplate.query("SELECT id, course_id, title, resource_url FROM crs_resource WHERE course_id = ? ORDER BY id",
                (rs, row) -> new Resource(rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), rs.getString("resource_url")), courseId);
    }

    private Optional<Course> oneCourse(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(course(rs)) : Optional.empty(); }
    private Optional<Member> oneMember(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(member(rs)) : Optional.empty(); }
    private Course course(ResultSet rs) throws SQLException { return new Course(rs.getLong("id"), rs.getString("name"), rs.getLong("teacher_id"), rs.getString("enrollment_mode"), rs.getString("status"), rs.getLong("roster_version")); }
    private Member member(ResultSet rs) throws SQLException { return new Member(rs.getLong("course_id"), rs.getLong("user_id"), rs.getString("role"), rs.getString("status"), rs.getLong("member_version")); }
    private Long nullableLong(ResultSet rs, String name) throws SQLException { long value = rs.getLong(name); return rs.wasNull() ? null : value; }

    public record Course(long id, String name, long teacherId, String enrollmentMode, String status, long rosterVersion) { }
    public record Member(long courseId, long userId, String role, String status, long memberVersion) { }
    public record Chapter(long id, long courseId, String title, Long parentId) { }
    public record Resource(long id, long courseId, String title, String url) { }
}
