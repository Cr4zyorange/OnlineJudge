package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class HomeworkService {
    private final JdbcTemplate jdbc;
    private final AssessmentOutboxRepository outbox;
    private final SourceGradeRepository grades;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public HomeworkService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox, SourceGradeRepository grades) {
        this(jdbc, outbox, grades, Clock.systemUTC());
    }

    HomeworkService(JdbcTemplate jdbc, AssessmentOutboxRepository outbox, SourceGradeRepository grades, Clock clock) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.grades = grades;
        this.clock = clock;
    }

    @Transactional
    public HomeworkSummary create(CreateHomeworkCommand command, String createdBy) {
        command.validate();
        Instant now = clock.instant();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator insert = connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO assessment_homework (course_id, title, description, type, status, deadline, total_score,
                    allow_resubmit, allow_late_submit, allowed_languages, created_by, aggregate_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, command.courseId());
            statement.setString(2, command.title().trim());
            statement.setString(3, command.description());
            statement.setString(4, command.type());
            statement.setTimestamp(5, Timestamp.from(command.deadline()));
            statement.setBigDecimal(6, command.totalScore());
            statement.setBoolean(7, command.allowResubmit());
            statement.setBoolean(8, command.allowLateSubmit());
            statement.setString(9, String.join(",", command.languages()));
            statement.setString(10, createdBy);
            statement.setTimestamp(11, Timestamp.from(now));
            statement.setTimestamp(12, Timestamp.from(now));
            return statement;
        };
        jdbc.update(insert, keyHolder);
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) throw new IllegalStateException("homework id was not generated");
        long homeworkId = generatedKey.longValue();
        for (TestCaseCommand testCase : command.testCases()) {
            jdbc.update("""
                    INSERT INTO assessment_homework_testcase
                        (homework_id, input_text, expected_output, score_weight, is_hidden, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, homeworkId, testCase.input(), testCase.expectedOutput(), testCase.scoreWeight(),
                    testCase.hidden(), testCase.sortOrder());
        }
        return find(homeworkId);
    }

    @Transactional
    public HomeworkSummary publish(long homeworkId, String correlationId) {
        HomeworkSummary homework = findForUpdate(homeworkId);
        if (!"DRAFT".equals(homework.status())) throw new IllegalStateException("only a draft homework can be published");
        if ("CODE".equals(homework.type()) && testcaseCount(homeworkId) == 0) {
            throw new IllegalArgumentException("code homework requires at least one test case");
        }
        Instant publishedAt = clock.instant();
        int updated = jdbc.update("""
                UPDATE assessment_homework
                   SET status = 'PUBLISHED', published_at = ?, aggregate_version = aggregate_version + 1, updated_at = ?
                 WHERE id = ? AND status = 'DRAFT'
                """, Timestamp.from(publishedAt), Timestamp.from(publishedAt), homeworkId);
        if (updated != 1) throw new IllegalStateException("homework publication conflicted");
        HomeworkSummary published = find(homeworkId);
        try {
            outbox.append("assessment.homework.published.v2", "assessment-homework", Long.toString(homeworkId),
                    published.aggregateVersion(), correlationId, Map.of(
                            "courseId", published.courseId(),
                            "homeworkId", Long.toString(homeworkId),
                            "title", published.title(),
                            "deadline", published.deadline().toString(),
                            "receiverScope", "COURSE_ACTIVE_STUDENTS",
                            "publishedAt", publishedAt.toString()), publishedAt);
        } catch (RuntimeException localFailure) {
            throw new HomeworkPublicationException(localFailure);
        }
        return published;
    }

    /** Grades become available to GRD only once a course manager explicitly publishes them. */
    @Transactional
    public HomeworkSummary publishScores(long homeworkId, String correlationId) {
        HomeworkSummary homework = findForUpdate(homeworkId);
        if (!"PUBLISHED".equals(homework.status())) {
            throw new IllegalStateException("only a published homework can have its scores published");
        }
        Instant publishedAt = clock.instant();
        List<PublishedScore> scores = jdbc.query("""
                SELECT hs.student_id, hs.final_score
                  FROM assessment_homework_submission hs
                  JOIN evaluation_task task ON task.submission_id = hs.submission_id
                 WHERE hs.homework_id = ? AND hs.is_final = TRUE
                   AND task.state = 'SUCCEEDED' AND hs.final_score IS NOT NULL
                """, (rs, ignored) -> new PublishedScore(rs.getString("student_id"), rs.getBigDecimal("final_score")), homeworkId);
        int updated = jdbc.update("""
                UPDATE assessment_homework
                   SET status = 'SCORE_PUBLISHED', aggregate_version = aggregate_version + 1, updated_at = ?
                 WHERE id = ? AND status = 'PUBLISHED'
                """, Timestamp.from(publishedAt), homeworkId);
        if (updated != 1) throw new IllegalStateException("homework score publication conflicted");
        for (PublishedScore score : scores) {
            long version = grades.upsertScored("HWK", Long.toString(homeworkId), homework.courseId(), score.studentId(),
                    score.score(), homework.totalScore(), publishedAt);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade",
                    "HWK:" + homeworkId + ":" + score.studentId(), version, correlationId,
                    Map.of("courseId", homework.courseId(), "sourceType", "HWK", "sourceId", Long.toString(homeworkId),
                            "studentId", score.studentId(), "score", score.score(), "fullScore", homework.totalScore(),
                            "status", "SCORED", "sourceVersion", version), publishedAt);
        }
        return find(homeworkId);
    }

    public HomeworkSummary find(long homeworkId) {
        return query(homeworkId, false);
    }

    private HomeworkSummary findForUpdate(long homeworkId) {
        return query(homeworkId, true);
    }

    private HomeworkSummary query(long homeworkId, boolean forUpdate) {
        String sql = """
                SELECT id, course_id, title, description, type, status, deadline, total_score,
                       allow_resubmit, allow_late_submit, allowed_languages, aggregate_version, published_at
                  FROM assessment_homework WHERE id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, ignored) -> new HomeworkSummary(rs.getLong("id"), rs.getString("course_id"),
                rs.getString("title"), rs.getString("description"), rs.getString("type"), rs.getString("status"),
                rs.getTimestamp("deadline").toInstant(), rs.getBigDecimal("total_score"),
                rs.getBoolean("allow_resubmit"), rs.getBoolean("allow_late_submit"),
                List.of(rs.getString("allowed_languages").split(",")), rs.getLong("aggregate_version"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant()), homeworkId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("homework not found"));
    }

    private int testcaseCount(long homeworkId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_testcase WHERE homework_id = ?", Integer.class, homeworkId);
    }

    public record CreateHomeworkCommand(String courseId, String title, String description, String type, Instant deadline,
                                        BigDecimal totalScore, boolean allowResubmit, boolean allowLateSubmit,
                                        List<String> languages, List<TestCaseCommand> testCases) {
        void validate() {
            if (courseId == null || courseId.isBlank() || title == null || title.isBlank() || title.trim().length() > 100) {
                throw new IllegalArgumentException("courseId and a 1-100 character title are required");
            }
            if (!"CODE".equals(type)) throw new IllegalArgumentException("the durable Assessment HWK slice currently accepts CODE homework");
            if (deadline == null || totalScore == null || totalScore.signum() <= 0) throw new IllegalArgumentException("deadline and positive totalScore are required");
            if (languages == null || languages.isEmpty() || languages.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("at least one language is required");
            }
            if (testCases == null || testCases.isEmpty()) throw new IllegalArgumentException("code homework requires at least one test case");
            BigDecimal configured = testCases.stream().map(TestCaseCommand::scoreWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (configured.compareTo(totalScore) != 0) throw new IllegalArgumentException("test case weights must equal totalScore");
        }
    }

    public record TestCaseCommand(String input, String expectedOutput, BigDecimal scoreWeight, boolean hidden, int sortOrder) {
        public TestCaseCommand {
            if (input == null || expectedOutput == null || scoreWeight == null || scoreWeight.signum() <= 0 || sortOrder < 1) {
                throw new IllegalArgumentException("test case input, output, positive weight and sortOrder are required");
            }
        }
    }

    public record HomeworkSummary(long id, String courseId, String title, String description, String type, String status,
                                  Instant deadline, BigDecimal totalScore, boolean allowResubmit, boolean allowLateSubmit,
                                  List<String> languages, long aggregateVersion, Instant publishedAt) { }

    private record PublishedScore(String studentId, BigDecimal score) { }
}
