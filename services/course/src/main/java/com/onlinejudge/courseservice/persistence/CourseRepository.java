package com.onlinejudge.courseservice.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** SQL for Course-owned DB-CRS-01..05 facts in oj_course only. */
@Repository
public class CourseRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public long createCourse(String name, String description, long teacherId, String enrollmentMode, String inviteCode, Integer maxStudents) {
        jdbcTemplate.update("""
                INSERT INTO crs_course
                    (course_name, description, teacher_id, enrollment_mode, invite_code, max_students, status, is_deleted, roster_version)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', FALSE, 0)
                """, name, description, teacherId, enrollmentMode, inviteCode, maxStudents);
        return jdbcTemplate.queryForObject("SELECT id FROM crs_course WHERE teacher_id = ? ORDER BY id DESC LIMIT 1", Long.class, teacherId);
    }

    public Optional<Course> findCourse(long courseId) {
        return jdbcTemplate.query("""
                SELECT id, course_name, description, teacher_id, enrollment_mode, invite_code, max_students,
                       status, is_deleted, roster_version
                  FROM crs_course WHERE id = ? AND is_deleted = FALSE
                """, this::oneCourse, courseId);
    }

    public List<Course> allCourses() {
        return jdbcTemplate.query("""
                SELECT id, course_name, description, teacher_id, enrollment_mode, invite_code, max_students,
                       status, is_deleted, roster_version
                  FROM crs_course WHERE is_deleted = FALSE ORDER BY id
                """, (rs, row) -> course(rs));
    }

    public Member insertMember(long courseId, long userId, String role, String status, String joinMethod, Long approvedBy) {
        jdbcTemplate.update("""
                INSERT INTO crs_course_member
                    (course_id, user_id, role, join_method, join_status, approved_by, joined_at, is_deleted, member_version)
                VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END, FALSE, 1)
                """, courseId, userId, role, joinMethod, status, approvedBy, status);
        return member(courseId, userId).orElseThrow();
    }

    public Optional<Member> member(long courseId, long userId) {
        return jdbcTemplate.query("""
                SELECT id, course_id, user_id, role, join_method, join_status, member_version
                  FROM crs_course_member WHERE course_id = ? AND user_id = ? AND is_deleted = FALSE
                """, this::oneMember, courseId, userId);
    }

    public List<Member> members(long courseId) {
        return jdbcTemplate.query("""
                SELECT id, course_id, user_id, role, join_method, join_status, member_version
                  FROM crs_course_member WHERE course_id = ? AND is_deleted = FALSE ORDER BY user_id
                """, (rs, row) -> member(rs), courseId);
    }

    public List<Member> members(long courseId, String role, String status, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT id, course_id, user_id, role, join_method, join_status, member_version FROM crs_course_member WHERE course_id = ? AND is_deleted = FALSE");
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        values.add(courseId);
        if (role != null && !role.isBlank()) { sql.append(" AND role = ?"); values.add(role); }
        if (status != null && !status.isBlank()) { sql.append(" AND join_status = ?"); values.add(status); }
        sql.append(" ORDER BY user_id LIMIT ? OFFSET ?"); values.add(size); values.add(page * size);
        return jdbcTemplate.query(sql.toString(), (rs, row) -> member(rs), values.toArray());
    }

    public long memberCount(long courseId, String role, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM crs_course_member WHERE course_id = ? AND is_deleted = FALSE");
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();
        values.add(courseId);
        if (role != null && !role.isBlank()) { sql.append(" AND role = ?"); values.add(role); }
        if (status != null && !status.isBlank()) { sql.append(" AND join_status = ?"); values.add(status); }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, values.toArray());
        return count == null ? 0 : count;
    }

    public long activeMemberCount(long courseId, String role) { return memberCount(courseId, role, "ACTIVE"); }

    public Member updateMember(long courseId, long userId, String role, String status, Long approvedBy) {
        jdbcTemplate.update("""
                UPDATE crs_course_member
                   SET role = ?, join_status = ?, approved_by = COALESCE(?, approved_by),
                       joined_at = CASE WHEN ? = 'ACTIVE' AND joined_at IS NULL THEN CURRENT_TIMESTAMP ELSE joined_at END,
                       left_at = CASE WHEN ? = 'REMOVED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                       member_version = member_version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ? AND is_deleted = FALSE
                """, role, status, approvedBy, status, status, courseId, userId);
        return member(courseId, userId).orElseThrow();
    }

    public Course updateCourse(long courseId, String name, String description, String enrollmentMode,
                               String inviteCode, Integer maxStudents, String status) {
        jdbcTemplate.update("""
                UPDATE crs_course
                   SET course_name = ?, description = ?, enrollment_mode = ?, invite_code = ?, max_students = ?,
                       status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND is_deleted = FALSE
                """, name, description, enrollmentMode, inviteCode, maxStudents, status, courseId);
        return findCourse(courseId).orElseThrow();
    }

    public Course archiveCourse(long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE", courseId);
        return findCourse(courseId).orElseThrow();
    }

    public long advanceRoster(long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET roster_version = roster_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE", courseId);
        return findCourse(courseId).orElseThrow().rosterVersion();
    }

    /** Legacy rows predate Course eventing; version one is their first canonical watermark. */
    public Course ensureCanonicalRosterVersion(long courseId) {
        jdbcTemplate.update("UPDATE crs_course SET roster_version = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND roster_version < 1 AND is_deleted = FALSE", courseId);
        return findCourse(courseId).orElseThrow();
    }

    public void createChapter(long courseId, String title, Long parentId, int sortOrder, String objective, boolean visible, int chapterType) {
        jdbcTemplate.update("""
                INSERT INTO crs_chapter
                    (course_id, parent_id, chapter_name, sort_order, objective, visible_status, chapter_type, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)
                """, courseId, parentId, title, sortOrder, objective, visible, chapterType);
    }

    public List<Chapter> chapters(long courseId, boolean includeHidden) {
        String sql = "SELECT id, course_id, chapter_name, parent_id, sort_order, objective, visible_status, chapter_type FROM crs_chapter WHERE course_id = ? AND is_deleted = FALSE" +
                (includeHidden ? "" : " AND visible_status = TRUE") + " ORDER BY parent_id, sort_order, id";
        return jdbcTemplate.query(sql, (rs, row) -> chapter(rs), courseId);
    }

    public Optional<Chapter> chapter(long courseId, long chapterId) {
        return jdbcTemplate.query("""
                SELECT id, course_id, chapter_name, parent_id, sort_order, objective, visible_status, chapter_type
                  FROM crs_chapter WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, rs -> rs.next() ? Optional.of(chapter(rs)) : Optional.empty(), courseId, chapterId);
    }

    public void updateChapter(long courseId, long chapterId, String title, Long parentId, int sortOrder, String objective, boolean visible, int chapterType) {
        jdbcTemplate.update("""
                UPDATE crs_chapter
                   SET chapter_name = ?, parent_id = ?, sort_order = ?, objective = ?, visible_status = ?, chapter_type = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, title, parentId, sortOrder, objective, visible, chapterType, courseId, chapterId);
    }

    public boolean chapterHasChildren(long courseId, long chapterId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_chapter WHERE course_id = ? AND parent_id = ? AND is_deleted = FALSE", Integer.class, courseId, chapterId);
        return count != null && count > 0;
    }

    public void deleteChapter(long courseId, long chapterId) {
        jdbcTemplate.update("UPDATE crs_chapter SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE course_id = ? AND id = ? AND is_deleted = FALSE", courseId, chapterId);
    }

    public void createResource(long courseId, Long chapterId, String name, String type, String visibility, LocalDateTime publishAt,
                               String storageKey, String externalUrl, String originalFilename, String contentType, long fileSize, long actorId) {
        jdbcTemplate.update("""
                INSERT INTO crs_resource
                    (course_id, chapter_id, resource_name, resource_type, visibility, publish_at, storage_key, external_url,
                     original_filename, content_type, file_size, version, download_count, upload_user_id, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, FALSE)
                """, courseId, chapterId, name, type, visibility, publishAt, storageKey, externalUrl, originalFilename, contentType, fileSize, actorId);
    }

    public List<Resource> resources(long courseId, boolean manager) {
        String sql = "SELECT id, course_id, chapter_id, resource_name, resource_type, visibility, publish_at, storage_key, external_url, original_filename, content_type, file_size, version, download_count, upload_user_id FROM crs_resource WHERE course_id = ? AND is_deleted = FALSE" +
                (manager ? "" : " AND visibility = 'STUDENT' AND (publish_at IS NULL OR publish_at <= CURRENT_TIMESTAMP)") + " ORDER BY id";
        return jdbcTemplate.query(sql, (rs, row) -> resource(rs), courseId);
    }

    public Optional<Resource> resource(long courseId, long resourceId) {
        return jdbcTemplate.query("""
                SELECT id, course_id, chapter_id, resource_name, resource_type, visibility, publish_at, storage_key, external_url,
                       original_filename, content_type, file_size, version, download_count, upload_user_id
                  FROM crs_resource WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, rs -> rs.next() ? Optional.of(resource(rs)) : Optional.empty(), courseId, resourceId);
    }

    public void updateResource(long courseId, long resourceId, Long chapterId, String name, String type, String visibility,
                               LocalDateTime publishAt, String externalUrl) {
        jdbcTemplate.update("""
                UPDATE crs_resource
                   SET chapter_id = ?, resource_name = ?, resource_type = ?, visibility = ?, publish_at = ?, external_url = ?,
                       version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND id = ? AND is_deleted = FALSE
                """, chapterId, name, type, visibility, publishAt, externalUrl, courseId, resourceId);
    }

    public void deleteResource(long courseId, long resourceId) {
        jdbcTemplate.update("UPDATE crs_resource SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE course_id = ? AND id = ? AND is_deleted = FALSE", courseId, resourceId);
    }

    public void incrementDownloadCount(long courseId, long resourceId) {
        jdbcTemplate.update("UPDATE crs_resource SET download_count = download_count + 1 WHERE course_id = ? AND id = ? AND is_deleted = FALSE", courseId, resourceId);
    }

    public Announcement createAnnouncement(long courseId, String title, String content, boolean top, long publisherId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO crs_announcement (course_id, title, content, is_top, publisher_id, is_deleted)
                    VALUES (?, ?, ?, ?, ?, FALSE)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, courseId);
            statement.setString(2, title);
            statement.setString(3, content);
            statement.setBoolean(4, top);
            statement.setLong(5, publisherId);
            return statement;
        }, keyHolder);
        return announcement(courseId, generatedId(keyHolder)).orElseThrow();
    }

    public List<Announcement> announcements(long courseId) {
        return jdbcTemplate.query("""
                SELECT id, course_id, title, content, is_top, publisher_id FROM crs_announcement
                 WHERE course_id = ? AND is_deleted = FALSE ORDER BY is_top DESC, created_at DESC, id DESC
                """, (rs, row) -> new Announcement(rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"),
                rs.getString("content"), rs.getBoolean("is_top"), rs.getLong("publisher_id")), courseId);
    }

    public Optional<Announcement> announcement(long courseId, long announcementId) {
        return jdbcTemplate.query("SELECT id, course_id, title, content, is_top, publisher_id FROM crs_announcement WHERE course_id = ? AND id = ? AND is_deleted = FALSE",
                rs -> rs.next() ? Optional.of(new Announcement(rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), rs.getString("content"), rs.getBoolean("is_top"), rs.getLong("publisher_id"))) : Optional.empty(), courseId, announcementId);
    }

    public void updateAnnouncement(long courseId, long announcementId, String title, String content, boolean top) {
        jdbcTemplate.update("UPDATE crs_announcement SET title = ?, content = ?, is_top = ?, updated_at = CURRENT_TIMESTAMP WHERE course_id = ? AND id = ? AND is_deleted = FALSE",
                title, content, top, courseId, announcementId);
    }

    public void deleteAnnouncement(long courseId, long announcementId) {
        jdbcTemplate.update("UPDATE crs_announcement SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE course_id = ? AND id = ? AND is_deleted = FALSE", courseId, announcementId);
    }

    private Optional<Course> oneCourse(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(course(rs)) : Optional.empty(); }
    private Optional<Member> oneMember(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(member(rs)) : Optional.empty(); }
    private Course course(ResultSet rs) throws SQLException { return new Course(rs.getLong("id"), rs.getString("course_name"), rs.getString("description"), rs.getLong("teacher_id"), rs.getString("enrollment_mode"), rs.getString("invite_code"), (Integer) rs.getObject("max_students"), rs.getString("status"), rs.getLong("roster_version")); }
    private Member member(ResultSet rs) throws SQLException { return new Member(rs.getLong("id"), rs.getLong("course_id"), rs.getLong("user_id"), rs.getString("role"), rs.getString("join_status"), rs.getLong("member_version"), rs.getString("join_method")); }
    private Chapter chapter(ResultSet rs) throws SQLException { return new Chapter(rs.getLong("id"), rs.getLong("course_id"), rs.getString("chapter_name"), nullableLong(rs, "parent_id"), rs.getInt("sort_order"), rs.getString("objective"), rs.getBoolean("visible_status"), rs.getInt("chapter_type")); }
    private Resource resource(ResultSet rs) throws SQLException { return new Resource(rs.getLong("id"), rs.getLong("course_id"), nullableLong(rs, "chapter_id"), rs.getString("resource_name"), rs.getString("resource_type"), rs.getString("visibility"), rs.getObject("publish_at", LocalDateTime.class), rs.getString("storage_key"), rs.getString("external_url"), rs.getString("original_filename"), rs.getString("content_type"), rs.getLong("file_size"), rs.getInt("version"), rs.getLong("download_count"), rs.getLong("upload_user_id")); }
    private Long nullableLong(ResultSet rs, String name) throws SQLException { long value = rs.getLong(name); return rs.wasNull() ? null : value; }
    private long generatedId(KeyHolder keyHolder) {
        if (keyHolder.getKeyList().isEmpty()) throw new IllegalStateException("no announcement generated key returned");
        Object value = keyHolder.getKeyList().getFirst().get("id");
        if (value == null) value = keyHolder.getKeyList().getFirst().values().iterator().next();
        return ((Number) value).longValue();
    }

    public record Course(long id, String name, String description, long teacherId, String enrollmentMode, String inviteCode, Integer maxStudents, String status, long rosterVersion) { }
    public record Member(long id, long courseId, long userId, String role, String status, long memberVersion, String joinMethod) { }
    public record Chapter(long id, long courseId, String title, Long parentId, int sortOrder, String objective, boolean visible, int chapterType) { }
    public record Resource(long id, long courseId, Long chapterId, String name, String type, String visibility, LocalDateTime publishAt,
                           String storageKey, String externalUrl, String originalFilename, String contentType, long fileSize, int version, long downloadCount, long uploadUserId) { }
    public record Announcement(long id, long courseId, String title, String content, boolean top, long publisherId) { }
}
