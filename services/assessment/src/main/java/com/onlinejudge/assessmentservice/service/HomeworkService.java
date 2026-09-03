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
        for (QuestionCommand question : command.questions()) {
            jdbc.update("""
                    INSERT INTO assessment_homework_question
                        (homework_id, question_type, stem, options_json, answer_json, score, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, homeworkId, question.questionType(), question.stem(), question.optionsJson(),
                    question.answerJson(), question.score(), question.sortOrder());
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
    public HomeworkSummary publishScores(long homeworkId, String publishedBy, String correlationId) {
        HomeworkSummary homework = findForUpdate(homeworkId);
        if (!"PUBLISHED".equals(homework.status())) {
            throw new IllegalStateException("only a published homework can have its scores published");
        }
        Instant publishedAt = clock.instant();
        // Lock every current submission/task pair before changing the homework state.  A worker
        // either finishes before this read and is included, or is observed as non-terminal and
        // publication remains retryable while the homework stays PUBLISHED.
        List<FinalSubmission> finalSubmissions = jdbc.query("""
                SELECT hs.submission_id, hs.student_id, hs.final_score, task.state AS task_state
                  FROM assessment_homework_submission hs
                  LEFT JOIN evaluation_task task ON task.submission_id = hs.submission_id
                 WHERE hs.homework_id = ? AND hs.is_final = TRUE
                 FOR UPDATE
                """, (rs, ignored) -> new FinalSubmission(rs.getString("submission_id"), rs.getString("student_id"),
                rs.getBigDecimal("final_score"), rs.getString("task_state")), homeworkId);
        if (finalSubmissions.stream().anyMatch(submission -> !submission.terminal())) {
            throw new IllegalStateException("all final homework submissions must reach a terminal evaluation state before scores can be published");
        }
        List<PublishedScore> scores = finalSubmissions.stream()
                .filter(FinalSubmission::scored)
                .map(submission -> new PublishedScore(submission.submissionId(), submission.studentId(), submission.finalScore()))
                .toList();
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
            jdbc.update("""
                    INSERT INTO assessment_homework_review_log
                        (submission_id, homework_id, student_id, operation_type, old_score, new_score, operator_id, reason, created_at)
                    VALUES (?, ?, ?, 'SCORE_PUBLISHED', NULL, ?, ?, 'teacher published homework scores', ?)
                    """, score.submissionId(), homeworkId, score.studentId(), score.score(), publishedBy,
                    Timestamp.from(publishedAt));
        }
        return find(homeworkId);
    }

    public HomeworkSummary find(long homeworkId) {
        return query(homeworkId, false);
    }

    public List<HomeworkSummary> list(String courseId, boolean includeDrafts) {
        String visibility = includeDrafts ? "" : " AND status IN ('PUBLISHED', 'SCORE_PUBLISHED')";
        return jdbc.query("""
                SELECT id, course_id, title, description, type, status, deadline, total_score,
                       allow_resubmit, allow_late_submit, allowed_languages, aggregate_version, published_at
                  FROM assessment_homework
                 WHERE course_id = ?
                """ + visibility + " ORDER BY id DESC", (rs, ignored) -> new HomeworkSummary(rs.getLong("id"),
                rs.getString("course_id"), rs.getString("title"), rs.getString("description"),
                rs.getString("type"), rs.getString("status"), rs.getTimestamp("deadline").toInstant(),
                rs.getBigDecimal("total_score"), rs.getBoolean("allow_resubmit"), rs.getBoolean("allow_late_submit"),
                splitLanguages(rs.getString("allowed_languages")), rs.getLong("aggregate_version"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant()), courseId);
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
                splitLanguages(rs.getString("allowed_languages")), rs.getLong("aggregate_version"),
                rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant()), homeworkId)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("homework not found"));
    }

    private int testcaseCount(long homeworkId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assessment_homework_testcase WHERE homework_id = ?", Integer.class, homeworkId);
    }

    private static List<String> splitLanguages(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return List.of(stored.split(","));
    }

    public record CreateHomeworkCommand(String courseId, String title, String description, String type, Instant deadline,
                                        BigDecimal totalScore, boolean allowResubmit, boolean allowLateSubmit,
                                        List<String> languages, List<TestCaseCommand> testCases,
                                        List<QuestionCommand> questions) {
        void validate() {
            if (courseId == null || courseId.isBlank() || title == null || title.isBlank() || title.trim().length() > 100) {
                throw new IllegalArgumentException("courseId and a 1-100 character title are required");
            }
            if (!List.of("TEXT", "OBJECTIVE", "FILE", "CODE").contains(type)) {
                throw new IllegalArgumentException("homework type must be TEXT, OBJECTIVE, FILE or CODE");
            }
            if (deadline == null || totalScore == null || totalScore.signum() <= 0) throw new IllegalArgumentException("deadline and positive totalScore are required");
            if (languages == null || testCases == null || questions == null) throw new IllegalArgumentException("languages, testCases and questions must be arrays");
            if ("CODE".equals(type)) {
                if (languages.isEmpty() || languages.stream().anyMatch(value -> value == null || value.isBlank())) {
                    throw new IllegalArgumentException("at least one language is required");
                }
                if (testCases.isEmpty()) throw new IllegalArgumentException("code homework requires at least one test case");
                BigDecimal configured = testCases.stream().map(TestCaseCommand::scoreWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (configured.compareTo(totalScore) != 0) throw new IllegalArgumentException("test case weights must equal totalScore");
            } else if (!languages.isEmpty() || !testCases.isEmpty()) {
                throw new IllegalArgumentException("only code homework accepts languages and testCases");
            }
            if ("OBJECTIVE".equals(type)) {
                if (questions.isEmpty()) throw new IllegalArgumentException("objective homework requires at least one question");
                BigDecimal configured = questions.stream().map(QuestionCommand::score).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (configured.compareTo(totalScore) != 0) throw new IllegalArgumentException("question scores must equal totalScore");
            } else if (!questions.isEmpty()) {
                throw new IllegalArgumentException("only objective homework accepts questions");
            }
        }
    }

    public record QuestionCommand(String questionType, String stem, String optionsJson, String answerJson,
                                  BigDecimal score, int sortOrder) {
        public QuestionCommand {
            if (questionType == null || questionType.isBlank() || stem == null || stem.isBlank()
                    || optionsJson == null || answerJson == null || score == null || score.signum() <= 0 || sortOrder < 1) {
                throw new IllegalArgumentException("objective question type, stem, options, answer, positive score and sortOrder are required");
            }
        }
    }

    public List<QuestionSummary> questions(long homeworkId) {
        return jdbc.query("""
                SELECT question_type, stem, options_json, answer_json, score, sort_order
                  FROM assessment_homework_question
                 WHERE homework_id = ?
                 ORDER BY sort_order, id
                """, (rs, ignored) -> new QuestionSummary(rs.getString("question_type"), rs.getString("stem"),
                rs.getString("options_json"), rs.getString("answer_json"), rs.getBigDecimal("score"),
                rs.getInt("sort_order")), homeworkId);
    }

    public record QuestionSummary(String questionType, String stem, String optionsJson, String answerJson,
                                  BigDecimal score, int sortOrder) { }

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

    private record FinalSubmission(String submissionId, String studentId, BigDecimal finalScore, String taskState) {
        boolean terminal() { return taskState == null ? finalScore != null : "SUCCEEDED".equals(taskState) || "FAILED".equals(taskState); }
        boolean scored() { return finalScore != null && (taskState == null || "SUCCEEDED".equals(taskState)); }
    }

    private record PublishedScore(String submissionId, String studentId, BigDecimal score) { }
}
