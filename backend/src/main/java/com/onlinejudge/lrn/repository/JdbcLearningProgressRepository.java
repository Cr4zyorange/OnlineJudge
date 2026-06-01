package com.onlinejudge.lrn.repository;

import com.onlinejudge.lrn.domain.LearningProgress;
import com.onlinejudge.lrn.domain.LearningStudentProgressRow;
import com.onlinejudge.lrn.service.LearningProgressSaveCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcLearningProgressRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningProgressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveCourseMember(long userId, long courseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crs_course_member member
                INNER JOIN crs_course course ON course.id = member.course_id
                WHERE member.user_id = ?
                  AND member.course_id = ?
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                """, Integer.class, userId, courseId);
        return count != null && count > 0;
    }

    public boolean chapterBelongsToCourse(long chapterId, long courseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crs_chapter
                WHERE id = ?
                  AND course_id = ?
                  AND is_deleted = FALSE
                """, Integer.class, chapterId, courseId);
        return count != null && count > 0;
    }

    public boolean canManageCourse(long userId, long courseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crs_course_member member
                INNER JOIN crs_course course ON course.id = member.course_id
                WHERE member.user_id = ?
                  AND member.course_id = ?
                  AND member.join_status = 'ACTIVE'
                  AND member.role IN ('TEACHER', 'ASSISTANT')
                  AND member.is_deleted = FALSE
                  AND course.is_deleted = FALSE
                """, Integer.class, userId, courseId);
        return count != null && count > 0;
    }

    public List<LearningStudentProgressRow> findStudentProgressByCourse(long courseId) {
        return jdbcTemplate.query("""
                SELECT member.user_id AS student_id,
                       auth.display_name AS student_name,
                       course.id AS course_id,
                       course.course_name AS course_name,
                       COALESCE(ROUND(AVG(progress.progress_percent)), 0) AS progress_percent,
                       MAX(progress.updated_at) AS updated_at
                FROM crs_course_member member
                INNER JOIN crs_course course
                    ON course.id = member.course_id
                    AND course.is_deleted = FALSE
                LEFT JOIN t_auth_user auth
                    ON auth.user_id = member.user_id
                    AND auth.deleted = FALSE
                LEFT JOIN lrn_learning_progress progress
                    ON progress.user_id = member.user_id
                    AND progress.course_id = member.course_id
                WHERE member.course_id = ?
                  AND member.role = 'STUDENT'
                  AND member.join_status = 'ACTIVE'
                  AND member.is_deleted = FALSE
                GROUP BY member.user_id, auth.display_name, course.id, course.course_name
                ORDER BY member.user_id
                """, this::mapStudentProgressRow, courseId);
    }

    public LearningProgress save(long userId, LearningProgressSaveCommand command, String status) {
        Optional<Long> existingId = findExistingId(
                userId,
                command.courseId(),
                command.sourceModule(),
                command.sourceId()
        );
        if (existingId.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE lrn_learning_progress
                    SET chapter_id = ?,
                        progress_percent = ?,
                        last_position = ?,
                        status = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    command.chapterId(),
                    command.progressPercent(),
                    command.lastPosition(),
                    status,
                    existingId.get());
            return findById(existingId.get()).orElseThrow();
        }

        jdbcTemplate.update("""
                INSERT INTO lrn_learning_progress
                    (user_id, course_id, chapter_id, source_module, source_id, progress_percent, last_position, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                command.courseId(),
                command.chapterId(),
                command.sourceModule(),
                command.sourceId(),
                command.progressPercent(),
                command.lastPosition(),
                status);
        return findById(findExistingId(userId, command.courseId(), command.sourceModule(), command.sourceId()).orElseThrow())
                .orElseThrow();
    }

    public List<LearningProgress> findByUser(long userId, Long courseId) {
        return jdbcTemplate.query("""
                SELECT progress.id,
                       progress.user_id,
                       progress.course_id,
                       course.course_name,
                       progress.chapter_id,
                       chapter.chapter_name,
                       progress.source_module,
                       progress.source_id,
                       progress.progress_percent,
                       progress.last_position,
                       progress.status,
                       progress.updated_at
                FROM lrn_learning_progress progress
                INNER JOIN crs_course_member member
                    ON member.course_id = progress.course_id
                    AND member.user_id = progress.user_id
                    AND member.join_status = 'ACTIVE'
                    AND member.is_deleted = FALSE
                INNER JOIN crs_course course
                    ON course.id = progress.course_id
                    AND course.is_deleted = FALSE
                LEFT JOIN crs_chapter chapter
                    ON chapter.id = progress.chapter_id
                    AND chapter.is_deleted = FALSE
                WHERE progress.user_id = ?
                  AND (? IS NULL OR progress.course_id = ?)
                ORDER BY course.id, COALESCE(chapter.sort_order, 0), chapter.id, progress.updated_at DESC, progress.id
                """, this::mapRow, userId, courseId, courseId);
    }

    private Optional<Long> findExistingId(long userId, long courseId, String sourceModule, long sourceId) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM lrn_learning_progress
                WHERE user_id = ?
                  AND course_id = ?
                  AND source_module = ?
                  AND source_id = ?
                """, (rs, rowNum) -> rs.getLong("id"), userId, courseId, sourceModule, sourceId);
        return ids.stream().findFirst();
    }

    private Optional<LearningProgress> findById(long id) {
        return jdbcTemplate.query("""
                SELECT progress.id,
                       progress.user_id,
                       progress.course_id,
                       course.course_name,
                       progress.chapter_id,
                       chapter.chapter_name,
                       progress.source_module,
                       progress.source_id,
                       progress.progress_percent,
                       progress.last_position,
                       progress.status,
                       progress.updated_at
                FROM lrn_learning_progress progress
                INNER JOIN crs_course course ON course.id = progress.course_id
                LEFT JOIN crs_chapter chapter ON chapter.id = progress.chapter_id
                WHERE progress.id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    private LearningProgress mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long chapterId = rs.getObject("chapter_id") == null ? null : rs.getLong("chapter_id");
        return new LearningProgress(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("course_id"),
                rs.getString("course_name"),
                chapterId,
                rs.getString("chapter_name"),
                rs.getString("source_module"),
                rs.getLong("source_id"),
                rs.getInt("progress_percent"),
                rs.getString("last_position"),
                rs.getString("status"),
                nullableDateTime(rs, "updated_at")
        );
    }

    private LearningStudentProgressRow mapStudentProgressRow(ResultSet rs, int rowNum) throws SQLException {
        long studentId = rs.getLong("student_id");
        String studentName = rs.getString("student_name");
        return new LearningStudentProgressRow(
                studentId,
                studentName == null || studentName.isBlank() ? "学生 " + studentId : studentName,
                rs.getLong("course_id"),
                rs.getString("course_name"),
                rs.getInt("progress_percent"),
                nullableDateTime(rs, "updated_at")
        );
    }

    private LocalDateTime nullableDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
