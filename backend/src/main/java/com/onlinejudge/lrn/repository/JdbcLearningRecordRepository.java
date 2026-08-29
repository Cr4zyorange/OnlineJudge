package com.onlinejudge.lrn.repository;

import com.onlinejudge.integration.learning.LearningCourseClient;
import com.onlinejudge.lrn.domain.LearningRecord;
import com.onlinejudge.lrn.service.LearningRecordCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcLearningRecordRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LearningCourseClient courseClient;

    public JdbcLearningRecordRepository(JdbcTemplate jdbcTemplate, LearningCourseClient courseClient) {
        this.jdbcTemplate = jdbcTemplate; this.courseClient = courseClient;
    }

    public boolean isActiveCourseMember(long userId, long courseId) { return courseClient.isActiveMember(userId, courseId); }

    public int countRecentReports(long userId, LearningRecordCommand command, LocalDateTime since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lrn_learning_record
                WHERE user_id=? AND course_id=? AND source_module=? AND source_id=? AND created_at>=?
                """, Integer.class, userId, command.courseId(), command.sourceModule(), command.sourceId(), since);
        return count == null ? 0 : count;
    }

    public LearningRecord save(long userId, LearningRecordCommand command) {
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_record
                    (user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, userId, command.courseId(), command.sourceModule(), command.sourceId(), command.actionType(),
                command.durationSeconds(), command.startedAt(), command.endedAt());
        return findLatest(userId, command).orElseThrow();
    }

    public List<LearningRecord> findByUserSince(long userId, Long courseId, LocalDateTime since) {
        List<RecordRow> rows = jdbcTemplate.query("""
                SELECT id,user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at,created_at
                FROM lrn_learning_record WHERE user_id=? AND (? IS NULL OR course_id=?) AND started_at>=?
                ORDER BY started_at DESC,id DESC
                """, this::mapData, userId, courseId, courseId, since);
        Set<Long> active = Set.copyOf(courseClient.findActiveCourseIds(userId));
        return enrich(rows.stream().filter(row -> active.contains(row.courseId())).toList());
    }

    private Optional<LearningRecord> findLatest(long userId, LearningRecordCommand command) {
        List<RecordRow> rows = jdbcTemplate.query("""
                SELECT id,user_id,course_id,source_module,source_id,action_type,duration,started_at,ended_at,created_at
                FROM lrn_learning_record WHERE user_id=? AND course_id=? AND source_module=? AND source_id=? AND action_type=?
                ORDER BY id DESC LIMIT 1
                """, this::mapData, userId, command.courseId(), command.sourceModule(), command.sourceId(), command.actionType());
        return enrich(rows).stream().findFirst();
    }

    private List<LearningRecord> enrich(List<RecordRow> rows) {
        Map<Long, String> names = courseClient.findCourseNames(rows.stream().map(RecordRow::courseId).collect(Collectors.toSet()));
        return rows.stream().map(row -> new LearningRecord(row.id(), row.userId(), row.courseId(),
                names.getOrDefault(row.courseId(), "课程 " + row.courseId()), row.sourceModule(), row.sourceId(),
                row.actionType(), row.duration(), row.startedAt(), row.endedAt(), row.createdAt())).toList();
    }

    private RecordRow mapData(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new RecordRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("course_id"),
                rs.getString("source_module"), rs.getLong("source_id"), rs.getString("action_type"),
                rs.getInt("duration"), rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("ended_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class));
    }

    private record RecordRow(long id, long userId, long courseId, String sourceModule, long sourceId,
                             String actionType, int duration, LocalDateTime startedAt,
                             LocalDateTime endedAt, LocalDateTime createdAt) {}
}
