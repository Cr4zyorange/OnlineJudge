package com.onlinejudge.lrn.repository;

import com.onlinejudge.integration.learning.LearningCourseClient;
import com.onlinejudge.integration.learning.LearningUserClient;
import com.onlinejudge.lrn.domain.LearningProgress;
import com.onlinejudge.lrn.domain.LearningStudentProgressRow;
import com.onlinejudge.lrn.service.LearningProgressSaveCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class JdbcLearningProgressRepository {
    private final JdbcTemplate jdbcTemplate;
    private final LearningCourseClient courseClient;
    private final LearningUserClient userClient;

    public JdbcLearningProgressRepository(JdbcTemplate jdbcTemplate, LearningCourseClient courseClient,
                                          LearningUserClient userClient) {
        this.jdbcTemplate = jdbcTemplate; this.courseClient = courseClient; this.userClient = userClient;
    }

    public boolean isActiveCourseMember(long userId, long courseId) { return courseClient.isActiveMember(userId, courseId); }
    public boolean chapterBelongsToCourse(long chapterId, long courseId) { return courseClient.chapterBelongs(chapterId, courseId); }
    public boolean canManageCourse(long userId, long courseId) { return courseClient.canManage(userId, courseId); }

    public List<LearningStudentProgressRow> findStudentProgressByCourse(long courseId) {
        Map<Long, Aggregate> aggregates = jdbcTemplate.query("""
                SELECT user_id,COALESCE(ROUND(AVG(progress_percent)),0),MAX(updated_at)
                FROM lrn_learning_progress WHERE course_id=? GROUP BY user_id
                """, (rs, n) -> new Aggregate(rs.getLong(1), rs.getInt(2), rs.getObject(3, LocalDateTime.class)), courseId)
                .stream().collect(Collectors.toMap(Aggregate::userId, Function.identity()));
        var students = courseClient.findActiveStudents(courseId);
        Map<Long, String> names = userClient.findDisplayNames(students.stream().map(LearningCourseClient.StudentMembership::userId).toList());
        return students.stream().map(student -> {
            Aggregate aggregate = aggregates.getOrDefault(student.userId(), new Aggregate(student.userId(), 0, null));
            return new LearningStudentProgressRow(student.userId(), names.getOrDefault(student.userId(), "学生 " + student.userId()),
                    courseId, student.courseName(), aggregate.progress(), aggregate.updatedAt());
        }).toList();
    }

    public LearningProgress save(long userId, LearningProgressSaveCommand command, String status) {
        Optional<Long> existingId = findExistingId(userId, command.courseId(), command.sourceModule(), command.sourceId());
        if (existingId.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE lrn_learning_progress SET chapter_id=?,progress_percent=?,last_position=?,status=?,updated_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """, command.chapterId(), command.progressPercent(), command.lastPosition(), status, existingId.get());
            return findById(existingId.get()).orElseThrow();
        }
        jdbcTemplate.update("""
                INSERT INTO lrn_learning_progress
                    (user_id,course_id,chapter_id,source_module,source_id,progress_percent,last_position,status)
                VALUES (?,?,?,?,?,?,?,?)
                """, userId, command.courseId(), command.chapterId(), command.sourceModule(), command.sourceId(),
                command.progressPercent(), command.lastPosition(), status);
        return findById(findExistingId(userId, command.courseId(), command.sourceModule(), command.sourceId()).orElseThrow()).orElseThrow();
    }

    public List<LearningProgress> findByUser(long userId, Long courseId) {
        List<ProgressRow> rows = jdbcTemplate.query("""
                SELECT id,user_id,course_id,chapter_id,source_module,source_id,progress_percent,last_position,status,updated_at
                FROM lrn_learning_progress WHERE user_id=? AND (? IS NULL OR course_id=?) ORDER BY course_id,updated_at DESC,id
                """, this::mapData, userId, courseId, courseId);
        Set<Long> activeCourses = Set.copyOf(courseClient.findActiveCourseIds(userId));
        List<ProgressRow> activeRows = rows.stream().filter(row -> activeCourses.contains(row.courseId())).toList();
        Map<Long, Integer> chapterOrders = courseClient.findChapterSortOrders(activeRows.stream()
                .map(ProgressRow::chapterId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return enrich(activeRows.stream().sorted(Comparator.comparingLong(ProgressRow::courseId)
                .thenComparingInt(row -> row.chapterId() == null ? 0 : chapterOrders.getOrDefault(row.chapterId(), 0))
                .thenComparing(row -> row.chapterId() == null ? 0L : row.chapterId())
                .thenComparing(ProgressRow::updatedAt, Comparator.reverseOrder())
                .thenComparingLong(ProgressRow::id)).toList());
    }

    private Optional<Long> findExistingId(long userId, long courseId, String sourceModule, long sourceId) {
        return jdbcTemplate.query("SELECT id FROM lrn_learning_progress WHERE user_id=? AND course_id=? AND source_module=? AND source_id=?",
                (rs, n) -> rs.getLong(1), userId, courseId, sourceModule, sourceId).stream().findFirst();
    }

    private Optional<LearningProgress> findById(long id) {
        List<ProgressRow> rows = jdbcTemplate.query("""
                SELECT id,user_id,course_id,chapter_id,source_module,source_id,progress_percent,last_position,status,updated_at
                FROM lrn_learning_progress WHERE id=?
                """, this::mapData, id);
        return enrich(rows).stream().findFirst();
    }

    private List<LearningProgress> enrich(List<ProgressRow> rows) {
        Map<Long, String> courses = courseClient.findCourseNames(rows.stream().map(ProgressRow::courseId).collect(Collectors.toSet()));
        Map<Long, String> chapters = courseClient.findChapterNames(rows.stream().map(ProgressRow::chapterId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream().map(row -> new LearningProgress(row.id(), row.userId(), row.courseId(),
                courses.getOrDefault(row.courseId(), "课程 " + row.courseId()), row.chapterId(),
                row.chapterId() == null ? null : chapters.get(row.chapterId()),
                row.sourceModule(), row.sourceId(), row.progress(), row.lastPosition(), row.status(), row.updatedAt())).toList();
    }

    private ProgressRow mapData(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Long chapter = rs.getObject("chapter_id") == null ? null : rs.getLong("chapter_id");
        return new ProgressRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("course_id"), chapter,
                rs.getString("source_module"), rs.getLong("source_id"), rs.getInt("progress_percent"),
                rs.getString("last_position"), rs.getString("status"), rs.getObject("updated_at", LocalDateTime.class));
    }

    private record Aggregate(long userId, int progress, LocalDateTime updatedAt) {}
    private record ProgressRow(long id, long userId, long courseId, Long chapterId, String sourceModule,
                               long sourceId, int progress, String lastPosition, String status, LocalDateTime updatedAt) {}
}
