package com.onlinejudge.crs.service;

import com.onlinejudge.integration.learning.LearningCourseClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdbcLearningCourseClient implements LearningCourseClient {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningCourseClient(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public boolean isActiveMember(long userId, long courseId) {
        return count("""
                SELECT COUNT(*) FROM crs_course_member m JOIN crs_course c ON c.id=m.course_id
                WHERE m.user_id=? AND m.course_id=? AND m.join_status='ACTIVE'
                  AND m.is_deleted=FALSE AND c.is_deleted=FALSE
                """, userId, courseId) > 0;
    }

    @Override
    public boolean canManage(long userId, long courseId) {
        return count("""
                SELECT COUNT(*) FROM crs_course_member m JOIN crs_course c ON c.id=m.course_id
                WHERE m.user_id=? AND m.course_id=? AND m.join_status='ACTIVE'
                  AND m.role IN ('TEACHER','ASSISTANT') AND m.is_deleted=FALSE AND c.is_deleted=FALSE
                """, userId, courseId) > 0;
    }

    @Override
    public boolean chapterBelongs(long chapterId, long courseId) {
        return count("SELECT COUNT(*) FROM crs_chapter WHERE id=? AND course_id=? AND is_deleted=FALSE",
                chapterId, courseId) > 0;
    }

    @Override
    public List<StudentMembership> findActiveStudents(long courseId) {
        return jdbcTemplate.query("""
                SELECT m.user_id,c.id,c.course_name FROM crs_course_member m
                JOIN crs_course c ON c.id=m.course_id AND c.is_deleted=FALSE
                WHERE m.course_id=? AND m.role='STUDENT' AND m.join_status='ACTIVE' AND m.is_deleted=FALSE
                ORDER BY m.user_id
                """, (rs, n) -> new StudentMembership(rs.getLong(1), rs.getLong(2), rs.getString(3)), courseId);
    }

    @Override
    public List<StudentMembership> findAllActiveStudents() {
        return jdbcTemplate.query("""
                SELECT m.user_id,c.id,c.course_name FROM crs_course_member m
                JOIN crs_course c ON c.id=m.course_id AND c.is_deleted=FALSE
                WHERE m.role='STUDENT' AND m.join_status='ACTIVE' AND m.is_deleted=FALSE
                ORDER BY m.user_id,c.id
                """, (rs, n) -> new StudentMembership(rs.getLong(1), rs.getLong(2), rs.getString(3)));
    }

    @Override
    public List<Long> findActiveCourseIds(long userId) {
        return jdbcTemplate.query("""
                SELECT m.course_id FROM crs_course_member m JOIN crs_course c ON c.id=m.course_id
                WHERE m.user_id=? AND m.join_status='ACTIVE' AND m.is_deleted=FALSE AND c.is_deleted=FALSE
                ORDER BY m.course_id
                """, (rs, n) -> rs.getLong(1), userId);
    }

    @Override
    public Map<Long, String> findCourseNames(Collection<Long> courseIds) {
        return names("crs_course", "course_name", courseIds, "is_deleted=FALSE");
    }

    @Override
    public Map<Long, String> findChapterNames(Collection<Long> chapterIds) {
        return names("crs_chapter", "chapter_name", chapterIds, "is_deleted=FALSE");
    }

    @Override
    public Map<Long, Integer> findChapterSortOrders(Collection<Long> chapterIds) {
        if (chapterIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(chapterIds.size(), "?"));
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT id,sort_order FROM crs_chapter WHERE is_deleted=FALSE AND id IN (" + placeholders + ")",
                        (rs, n) -> Map.entry(rs.getLong(1), rs.getInt(2)), chapterIds.toArray())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public List<ExternalTask> findResourceTasks(long userId) {
        return jdbcTemplate.query("""
                SELECT r.id+1000000000,r.course_id,c.course_name,r.id,r.resource_name,r.created_at,r.updated_at
                FROM crs_resource r JOIN crs_course c ON c.id=r.course_id
                JOIN crs_course_member m ON m.course_id=r.course_id AND m.user_id=?
                WHERE r.is_deleted=FALSE AND c.is_deleted=FALSE AND m.join_status='ACTIVE' AND m.is_deleted=FALSE
                """, (rs, n) -> new ExternalTask(rs.getLong(1), rs.getLong(2), rs.getString(3), "CRS",
                rs.getLong(4), "RESOURCE", rs.getString(5), null, "/courses/" + rs.getLong(2),
                dateTime(rs, 6), dateTime(rs, 7)), userId);
    }

    private Map<Long, String> names(String table, String column, Collection<Long> ids, String condition) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, String> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT id," + column + " FROM " + table + " WHERE " + condition + " AND id IN (" + placeholders + ")",
                (rs, n) -> Map.entry(rs.getLong(1), rs.getString(2)), ids.toArray())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private LocalDateTime dateTime(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(index);
        return value == null ? null : value.toLocalDateTime();
    }
}
