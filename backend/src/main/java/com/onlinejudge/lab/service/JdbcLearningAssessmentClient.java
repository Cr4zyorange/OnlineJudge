package com.onlinejudge.lab.service;

import com.onlinejudge.integration.learning.LearningAssessmentClient;
import com.onlinejudge.integration.learning.LearningCourseClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class JdbcLearningAssessmentClient implements LearningAssessmentClient {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningAssessmentClient(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public List<LearningCourseClient.ExternalTask> findTasks(long userId, Collection<Long> courseIds) {
        if (courseIds.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(courseIds.size(), "?"));
        List<LearningCourseClient.ExternalTask> result = new ArrayList<>();
        result.addAll(jdbcTemplate.query("""
                SELECT id+2000000000,course_id,id,title,deadline,created_at,updated_at
                FROM lab_experiment WHERE deleted=FALSE AND status IN ('PUBLISHED','CLOSED','SCORE_PUBLISHED')
                  AND course_id IN (%s)
                """.formatted(placeholders), (rs, n) -> task(rs, "LAB", "EXPERIMENT"), courseIds.toArray()));
        result.addAll(jdbcTemplate.query("""
                SELECT id+3000000000,course_id,id,title,deadline,created_at,updated_at
                FROM t_hwk_homework WHERE is_deleted=FALSE AND status IN ('PUBLISHED','CLOSED','SCORE_PUBLISHED')
                  AND course_id IN (%s)
                """.formatted(placeholders), (rs, n) -> task(rs, "HWK", "HOMEWORK"), courseIds.toArray()));
        return result;
    }

    @Override
    public List<DeadlineTask> findUpcomingTasks(long userId, Collection<Long> courseIds,
                                                LocalDateTime start, LocalDateTime end, String sourceModule) {
        if (courseIds.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(courseIds.size(), "?"));
        List<Object> args = new ArrayList<>(courseIds);
        args.add(userId); args.add(start); args.add(end);
        if ("HWK".equals(sourceModule)) {
            return jdbcTemplate.query("""
                    SELECT h.course_id,h.id,h.title,h.deadline FROM t_hwk_homework h
                    WHERE h.course_id IN (%s) AND h.status='PUBLISHED' AND h.is_deleted=FALSE
                      AND NOT EXISTS (SELECT 1 FROM t_hwk_submission s WHERE s.homework_id=h.id AND s.student_id=? AND s.is_deleted=FALSE)
                      AND h.deadline>? AND h.deadline<=? ORDER BY h.deadline,h.id
                    """.formatted(placeholders), (rs, n) -> deadline(rs, "HWK"), args.toArray());
        }
        return jdbcTemplate.query("""
                SELECT l.course_id,l.id,l.title,l.deadline FROM lab_experiment l
                WHERE l.course_id IN (%s) AND l.status='PUBLISHED' AND l.deleted=FALSE
                  AND NOT EXISTS (SELECT 1 FROM lab_submission s WHERE s.lab_id=l.id AND s.student_id=? AND s.deleted=FALSE)
                  AND l.deadline>? AND l.deadline<=? ORDER BY l.deadline,l.id
                """.formatted(placeholders), (rs, n) -> deadline(rs, "LAB"), args.toArray());
    }

    private LearningCourseClient.ExternalTask task(ResultSet rs, String module, String type) throws SQLException {
        long courseId = rs.getLong(2); long sourceId = rs.getLong(3);
        String segment = "LAB".equals(module) ? "labs" : "homeworks";
        return new LearningCourseClient.ExternalTask(rs.getLong(1), courseId, null, module, sourceId, type,
                rs.getString(4), dateTime(rs, 5), "/courses/" + courseId + "/" + segment + "/" + sourceId,
                dateTime(rs, 6), dateTime(rs, 7));
    }

    private DeadlineTask deadline(ResultSet rs, String module) throws SQLException {
        return new DeadlineTask(rs.getLong(1), rs.getLong(2), module, rs.getString(3), dateTime(rs, 4));
    }

    private LocalDateTime dateTime(ResultSet rs, int index) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(index);
        return value == null ? null : value.toLocalDateTime();
    }
}
