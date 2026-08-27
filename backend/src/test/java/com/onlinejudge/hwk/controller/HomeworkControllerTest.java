package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.service.HomeworkEvaluationRecovery;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.hwk.evaluation.recovery-enabled=true"
})
@AutoConfigureMockMvc
@Sql(
        scripts = {
                "file:../database/migrations/20260530_01_create_hwk_homework.sql",
                "file:../database/migrations/20260601_01_create_hwk_submission.sql",
                "file:../database/migrations/20260602_01_create_hwk_evaluation.sql",
                "file:../database/migrations/20260602_02_create_hwk_review_log.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
@Sql(
        statements = {
                "DELETE FROM t_hwk_review_log",
                "DELETE FROM t_hwk_evaluation",
                "DELETE FROM t_hwk_test_case",
                "DELETE FROM t_hwk_question",
                "DELETE FROM t_hwk_judge_config",
                "DELETE FROM t_hwk_submission",
                "DELETE FROM t_hwk_homework"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class HomeworkControllerTest {
    @TestConfiguration
    static class NotificationPublisherTestConfig {
        @Bean
        @Primary
        RecordingNotificationEventPublisher recordingNotificationEventPublisher() {
            return new RecordingNotificationEventPublisher();
        }

        @Bean
        @Primary
        Evaluator deterministicHomeworkEvaluator() {
            return task -> {
                if (task.sourceCode() != null && task.sourceCode().contains("#FAKE_SYSTEM_ERROR")) {
                    throw new IllegalStateException("sandbox unavailable");
                }
                boolean wrong = task.sourceCode() != null && task.sourceCode().contains("#FAKE_WRONG");
                String expectedOutput = task.options().getOrDefault("expectedOutput", "");
                return new EvaluationResult(
                        task.taskId(),
                        wrong ? EvaluationStatus.WRONG_ANSWER : EvaluationStatus.ACCEPTED,
                        wrong ? BigDecimal.ZERO : BigDecimal.ONE,
                        wrong ? "wrong answer expected %s actual wrong output".formatted(expectedOutput) : "accepted",
                        List.of(wrong ? "wrong output" : expectedOutput),
                        LocalDateTime.now()
                );
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private RecordingNotificationEventPublisher notificationEventPublisher;

    @Autowired
    private SourceGradeClient sourceGradeClient;

    @Autowired
    private HomeworkEvaluationRecovery evaluationRecovery;

    @BeforeEach
    void clearNotificationEvents() {
        notificationEventPublisher.clear();
    }

    @Test
    void teacherCreatesObjectiveHomeworkDraftAndSavesQuestions() throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectivePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.type").value("OBJECTIVE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalScore").value(100))
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long homeworkId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/homeworks")
                        .headers(teacherHeaders("101", "101"))
                        .param("courseId", "101")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].id").value(homeworkId))
                .andExpect(jsonPath("$.data.list[0].title").value("HWK01 objective draft"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/questions", homeworkId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of(
                                        "questionType", "SINGLE_CHOICE",
                                        "stem", "2 + 2 = ?",
                                        "optionsJson", "[\"3\",\"4\"]",
                                        "answerJson", "[\"4\"]",
                                        "score", 20,
                                        "sortOrder", 1
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(1)))
                .andExpect(jsonPath("$.data.questions[0].stem").value("2 + 2 = ?"));
    }

    @Test
    void courseManagerSoftDeletesDraftAndPreservesHomeworkHistory() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        jdbcTemplate.update("""
                INSERT INTO t_hwk_question
                (homework_id, question_type, stem, options_json, answer_json, score, sort_order, created_at, updated_at)
                VALUES (?, 'JUDGE', '保留的历史题目', '[\"true\",\"false\"]', '[\"true\"]', 10, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, homeworkId);
        long submissionId = insertSubmission(homeworkId, 601, "CODE", "SUBMITTED", "ACCEPTED", "REVIEWED",
                100, BigDecimal.valueOf(100), 1, true, false, "2026-08-21 10:00:00");
        insertEvaluation(submissionId, homeworkId, 601);
        insertReviewLog(submissionId, homeworkId, 601);
        jdbcTemplate.update(
                "UPDATE t_hwk_homework SET updated_at = TIMESTAMP '2026-08-20 08:00:00' WHERE id = ?",
                homeworkId
        );

        Map<String, List<Map<String, Object>>> before = childRows(homeworkId);
        Long judgeConfigIdBefore = jdbcTemplate.queryForObject(
                "SELECT judge_config_id FROM t_hwk_homework WHERE id = ?",
                Long.class,
                homeworkId
        );
        String updatedBefore = jdbcTemplate.queryForObject(
                "SELECT CAST(updated_at AS VARCHAR) FROM t_hwk_homework WHERE id = ?",
                String.class,
                homeworkId
        );

        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(homeworkId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        Map<String, Object> parent = jdbcTemplate.queryForMap(
                "SELECT status, is_deleted, judge_config_id, CAST(updated_at AS VARCHAR) AS updated_at FROM t_hwk_homework WHERE id = ?",
                homeworkId
        );
        assertThat(parent.get("status")).isEqualTo("DRAFT");
        assertThat(((Number) parent.get("is_deleted")).intValue()).isEqualTo(1);
        assertThat(((Number) parent.get("judge_config_id")).longValue()).isEqualTo(judgeConfigIdBefore);
        assertThat(parent.get("updated_at")).isNotEqualTo(updatedBefore);
        assertThat(childRows(homeworkId)).isEqualTo(before);

        mockMvc.perform(get("/api/v1/homeworks")
                        .headers(teacherHeaders("101", "101"))
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list", hasSize(0)));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4001"));
        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4001"));
    }

    @Test
    void draftDeletionRequiresCourseManagementPermission() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());

        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM t_hwk_homework WHERE id = ?",
                Boolean.class,
                homeworkId
        )).isFalse();
    }

    @Test
    void onlyDraftHomeworkCanBeDeleted() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());

        for (String statusName : List.of("NOT_OPEN", "PUBLISHED", "CLOSED", "SCORE_PUBLISHED", "ARCHIVED")) {
            jdbcTemplate.update(
                    "UPDATE t_hwk_homework SET status = ?, is_deleted = FALSE WHERE id = ?",
                    statusName,
                    homeworkId
            );

            mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", homeworkId)
                            .headers(teacherHeaders("101", "101")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("HWK_4095"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM t_hwk_homework WHERE id = ?",
                Boolean.class,
                homeworkId
        )).isFalse();
    }

    @Test
    void staleEditAndPublishCannotRestoreDeletedDraft() throws Exception {
        long editHomeworkId = createHomeworkAndReturnId(textPayload());
        Homework staleEdit = homeworkRepository.findById(editHomeworkId).orElseThrow();
        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", editHomeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk());

        homeworkRepository.update(staleEdit.update(
                staleEdit.chapterId(),
                "过期编辑不得复活作业",
                staleEdit.description(),
                staleEdit.type(),
                staleEdit.totalScore(),
                staleEdit.deadline(),
                staleEdit.allowResubmit(),
                staleEdit.allowLateSubmit(),
                staleEdit.showEvaluationBeforePublish(),
                LocalDateTime.now(),
                staleEdit.questions(),
                staleEdit.testCases(),
                staleEdit.judgeConfig()
        ));

        long publishHomeworkId = createHomeworkAndReturnId(objectivePayload());
        Homework stalePublish = homeworkRepository.findById(publishHomeworkId).orElseThrow();
        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}", publishHomeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk());
        homeworkRepository.update(stalePublish.publish(LocalDateTime.now()));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, status, is_deleted FROM t_hwk_homework WHERE id IN (?, ?) ORDER BY id",
                editHomeworkId,
                publishHomeworkId
        );
        assertThat(rows).allSatisfy(row ->
                assertThat(((Number) row.get("is_deleted")).intValue()).isEqualTo(1)
        );
        assertThat(rows.get(0).get("title")).isEqualTo(staleEdit.title());
        assertThat(rows.get(0).get("status")).isEqualTo("DRAFT");
        assertThat(rows.get(1).get("status")).isEqualTo("DRAFT");
        assertThat(notificationEventPublisher.events()).isEmpty();
    }

    @Test
    void teacherPublishesConfiguredHomeworkAndNotificationIsEmitted() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:7001,7002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        assertThat(notificationEventPublisher.events()).hasSize(1);
        NotificationEvent event = notificationEventPublisher.events().get(0);
        assertThat(event.type()).isEqualTo("HOMEWORK_PUBLISHED");
        assertThat(event.courseId()).isEqualTo(101L);
        assertThat(event.recipientUserIds()).containsExactly(7001L, 7002L);
        assertThat(event.targetType()).isEqualTo("HWK");
        assertThat(event.targetId()).isEqualTo(homeworkId);

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/close", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void assistantCourseManagerCreatesConfiguresAndPublishesHomework() throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(assistantHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectivePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long homeworkId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/questions", homeworkId)
                        .headers(assistantHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of(
                                        "questionType", "SINGLE_CHOICE",
                                        "stem", "2 + 2 = ?",
                                        "optionsJson", "[\"3\",\"4\"]",
                                        "answerJson", "[\"4\"]",
                                        "score", 100,
                                        "sortOrder", 1
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(1)));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(assistantHeaders("101", "101", "101:601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void studentListKeepsClosedHomeworkVisibleForFeedbackAndHistoryEntry() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/close", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/homeworks")
                        .headers(studentHeaders("101"))
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].id").value(homeworkId))
                .andExpect(jsonPath("$.data.list[0].status").value("CLOSED"));
    }

    @Test
    void studentPublishedHomeworkListAndDetailDoNotExposeAnswersOrHiddenTestCaseOutput() throws Exception {
        long objectiveHomeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", objectiveHomeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long codeHomeworkId = createHomeworkAndReturnId(Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 code with hidden case"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", "[\"java\"]"),
                entry("timeLimitMs", 1000),
                entry("memoryLimitKb", 65536),
                entry("outputCompareMode", "EXACT"),
                entry("testCases", List.of(
                        Map.of(
                                "inputData", "1 2",
                                "expectedOutput", "3",
                                "scoreWeight", 50,
                                "hidden", false,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "sortOrder", 1
                        ),
                        Map.of(
                                "inputData", "40 2",
                                "expectedOutput", "42",
                                "scoreWeight", 50,
                                "hidden", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "sortOrder", 2
                        )
                ))
        ));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", codeHomeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/homeworks")
                        .headers(studentHeaders("101"))
                        .param("courseId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list", hasSize(2)))
                .andExpect(jsonPath("$.data.list[0].questions").isEmpty())
                .andExpect(jsonPath("$.data.list[0].testCases").isEmpty());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", objectiveHomeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andExpect(jsonPath("$.data.questions[0].answerJson").doesNotExist());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", codeHomeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.languageLimitJson").value("[\"java\"]"))
                .andExpect(jsonPath("$.data.timeLimitMs").doesNotExist())
                .andExpect(jsonPath("$.data.memoryLimitKb").doesNotExist())
                .andExpect(jsonPath("$.data.outputCompareMode").doesNotExist())
                .andExpect(jsonPath("$.data.testCases", hasSize(1)))
                .andExpect(jsonPath("$.data.testCases[0].hidden").value(false))
                .andExpect(jsonPath("$.data.testCases[0].expectedOutput").value("3"));
    }

    @Test
    void studentSubmitsPublishedTextHomeworkAndReceivesSubmissionReceipt() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answerText", "I would solve it with dynamic programming.",
                                "answerJson", "{\"q1\":\"B\"}",
                                "codeText", "",
                                "language", ""
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").isNumber())
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("UNREVIEWED"))
                .andExpect(jsonPath("$.data.final").value(true))
                .andExpect(jsonPath("$.data.submittedAt").exists());
    }

    @Test
    void studentSubmissionHistoryKeepsPreviousVersionsAndMarksOnlyLatestFinal() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        submitTextAnswer(homeworkId, "first answer", studentHeaders("101"));
        submitTextAnswer(homeworkId, "second answer", studentHeaders("101"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/my-submissions", homeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].final").value(true))
                .andExpect(jsonPath("$.data[0].answerText").value("second answer"))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].final").value(false))
                .andExpect(jsonPath("$.data[1].answerText").value("first answer"));

        int finalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id = ? AND student_id = 601 AND is_final = TRUE",
                Integer.class,
                homeworkId
        );
        assertThat(finalCount).isEqualTo(1);
    }

    @Test
    void courseManagerListsSubmissionsWithPaginationAndReadsSubmissionDetail() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602")))
                .andExpect(status().isOk());

        long firstSubmissionId = submitTextAnswer(homeworkId, "student 601 answer", studentHeaders("101", "601"));
        submitTextAnswer(homeworkId, "student 602 answer", studentHeaders("101", "602"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(assistantHeaders("101", "101"))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].homeworkId").value(homeworkId));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", firstSubmissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(firstSubmissionId))
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.answerText").value("student 601 answer"));
    }

    @Test
    void courseManagerFiltersSubmissionsByStudentAndStatuses() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603")))
                .andExpect(status().isOk());

        submitTextAnswer(homeworkId, "student 601 submitted answer", studentHeaders("101", "601"));
        long matchingSubmissionId = submitTextAnswer(homeworkId, "student 602 late pending answer", studentHeaders("101", "602"));
        submitTextAnswer(homeworkId, "student 603 submitted answer", studentHeaders("101", "603"));
        jdbcTemplate.update("""
                        UPDATE t_hwk_submission
                        SET submit_status = 'LATE',
                            evaluation_status = 'PENDING',
                            review_status = 'NEED_REVIEW'
                        WHERE id = ?
                        """,
                matchingSubmissionId
        );

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(teacherHeaders("101", "101"))
                        .param("studentKeyword", "602")
                        .param("submitStatus", "LATE")
                        .param("evaluationStatus", "PENDING")
                        .param("reviewStatus", "NEED_REVIEW")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].submissionId").value(matchingSubmissionId))
                .andExpect(jsonPath("$.data.list[0].studentId").value(602))
                .andExpect(jsonPath("$.data.list[0].submitStatus").value("LATE"))
                .andExpect(jsonPath("$.data.list[0].evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.list[0].reviewStatus").value("NEED_REVIEW"));
    }

    @Test
    void studentCannotReadAnotherStudentsSubmission() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602")))
                .andExpect(status().isOk());

        long submissionId = submitTextAnswer(homeworkId, "private answer", studentHeaders("101", "601"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(studentHeaders("101", "602")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void studentHistoryAndDetailHideUnpublishedScoresAndTeacherComment() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long submissionId = submitTextAnswer(homeworkId, "answer awaiting review", studentHeaders("101"));
        jdbcTemplate.update("""
                        UPDATE t_hwk_submission
                        SET manual_score = 88,
                            final_score = 90,
                            comment = 'private teacher feedback',
                            review_status = 'REVIEWED'
                        WHERE id = ?
                        """,
                submissionId
        );

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/my-submissions", homeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].submissionId").value(submissionId))
                .andExpect(jsonPath("$.data[0].answerText").value("answer awaiting review"))
                .andExpect(jsonPath("$.data[0].manualScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].finalScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].comment").doesNotExist());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerText").value("answer awaiting review"))
                .andExpect(jsonPath("$.data.manualScore").doesNotExist())
                .andExpect(jsonPath("$.data.finalScore").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualScore").value(88))
                .andExpect(jsonPath("$.data.finalScore").value(90))
                .andExpect(jsonPath("$.data.comment").value("private teacher feedback"));
    }

    @Test
    void objectiveHomeworkSubmissionShowsEvaluationButHidesUnpublishedFinalScore() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answerJson", "{\"q1\":[\"2\"],\"q2\":[\"true\"]}"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.autoScore").value(100))
                .andExpect(jsonPath("$.data.manualScore").doesNotExist())
                .andExpect(jsonPath("$.data.finalScore").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist());
    }

    @Test
    void scorePublishExposesStudentFeedbackAndHomeworkSourceGrades() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602")))
                .andExpect(status().isOk());

        long firstSubmissionId = submitTextAnswer(homeworkId, "student 601 answer", studentHeaders("101", "601"));
        long secondSubmissionId = submitTextAnswer(homeworkId, "student 602 answer", studentHeaders("101", "602"));
        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", firstSubmissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", new BigDecimal("88.00"),
                                "finalScore", new BigDecimal("90.00"),
                                "comment", "clear reasoning"
                        ))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", secondSubmissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", new BigDecimal("70.00"),
                                "finalScore", new BigDecimal("72.00"),
                                "comment", "needs more detail"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", firstSubmissionId)
                        .headers(studentHeaders("101", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist());

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", firstSubmissionId)
                        .headers(studentHeaders("101", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(90))
                .andExpect(jsonPath("$.data.comment").value("clear reasoning"));

        assertThat(notificationEventPublisher.events()).extracting(NotificationEvent::type)
                .contains("HOMEWORK_SCORE_PUBLISHED");
        NotificationEvent scoreEvent = notificationEventPublisher.events().stream()
                .filter(event -> event.type().equals("HOMEWORK_SCORE_PUBLISHED"))
                .findFirst()
                .orElseThrow();
        assertThat(scoreEvent.courseId()).isEqualTo(101L);
        assertThat(scoreEvent.recipientUserIds()).containsExactly(601L, 602L);
        assertThat(scoreEvent.targetType()).isEqualTo("HWK");
        assertThat(scoreEvent.targetId()).isEqualTo(homeworkId);

        assertThat(sourceGradeClient.findSourceGrades(101L, SourceGradeType.HWK, homeworkId))
                .extracting("studentId", "score", "fullScore", "status")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(601L, new BigDecimal("90.00"), new BigDecimal("100"), "SCORED"),
                        org.assertj.core.groups.Tuple.tuple(602L, new BigDecimal("72.00"), new BigDecimal("100"), "SCORED")
                );
        assertThat(sourceGradeClient.findSourceGrades(101L, SourceGradeType.HWK, 401L)).isEmpty();

        long scoreEventCount = notificationEventPublisher.events().stream()
                .filter(event -> event.type().equals("HOMEWORK_SCORE_PUBLISHED"))
                .count();
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/scores/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));
        assertThat(notificationEventPublisher.events().stream()
                .filter(event -> event.type().equals("HOMEWORK_SCORE_PUBLISHED"))
                .count()).isEqualTo(scoreEventCount);
    }

    @Test
    void teacherQueriesHomeworkStatisticsWithUnsubmittedStudentsAndScoreSummary() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603")))
                .andExpect(status().isOk());
        submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"true\"]}", studentHeaders("101", "601"));
        submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"false\"]}", studentHeaders("101", "602"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(assistantHeaders("101", "101", "101:601,602,603")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.totalStudentCount").value(3))
                .andExpect(jsonPath("$.data.submittedCount").value(2))
                .andExpect(jsonPath("$.data.unsubmittedCount").value(1))
                .andExpect(jsonPath("$.data.autoEvaluableCount").value(2))
                .andExpect(jsonPath("$.data.evaluatedCount").value(2))
                .andExpect(jsonPath("$.data.pendingEvaluationCount").value(0))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(0))
                .andExpect(jsonPath("$.data.reviewedCount").value(2))
                .andExpect(jsonPath("$.data.scoredCount").value(2))
                .andExpect(jsonPath("$.data.averageScore").value(70.00))
                .andExpect(jsonPath("$.data.maxScore").value(100))
                .andExpect(jsonPath("$.data.minScore").value(40))
                .andExpect(jsonPath("$.data.unsubmittedPage").value(1))
                .andExpect(jsonPath("$.data.unsubmittedSize").value(20))
                .andExpect(jsonPath("$.data.unsubmittedTotal").value(1))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds", hasSize(1)))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[0]").value(603))
                .andExpect(jsonPath("$.data.scoreDistribution['0-59']").value(1))
                .andExpect(jsonPath("$.data.scoreDistribution['60-69']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['70-79']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['80-89']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['90-100']").value(1))
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    void teacherQueriesHomeworkStatisticsWithPaginatedUnsubmittedStudentsForNfrPerformance() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603,604,605")))
                .andExpect(status().isOk());
        submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"true\"]}", studentHeaders("101", "601"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics?page=2&size=2", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603,604,605")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudentCount").value(5))
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.unsubmittedCount").value(4))
                .andExpect(jsonPath("$.data.unsubmittedPage").value(2))
                .andExpect(jsonPath("$.data.unsubmittedSize").value(2))
                .andExpect(jsonPath("$.data.unsubmittedTotal").value(4))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds", hasSize(2)))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[0]").value(604))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[1]").value(605));
    }

    @Test
    void statisticsUsesOnlyCurrentRosterFinalEffectiveSubmissionsAndNormalizesEveryBoundary() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        jdbcTemplate.update("UPDATE t_hwk_homework SET total_score = 50 WHERE id = ?", homeworkId);
        String submittedAt = "2026-08-22 10:00:00";

        insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("29.99"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 602, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("30.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 603, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("34.99"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 604, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("35.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 605, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("39.99"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 606, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("40.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 607, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("44.99"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 608, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("45.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 609, "TEXT", "LATE", "NONE", "REVIEWED", null,
                new BigDecimal("50.00"), 1, true, false, submittedAt);

        insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("50.00"), 2, false, false, submittedAt);
        insertSubmission(homeworkId, 610, "TEXT", "REJECTED", "NONE", "REVIEWED", null,
                new BigDecimal("50.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 611, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("50.00"), 1, true, true, submittedAt);
        insertSubmission(homeworkId, 999, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("50.00"), 1, true, false, submittedAt);

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603,604,605,606,607,608,609,610,611")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudentCount").value(11))
                .andExpect(jsonPath("$.data.submittedCount").value(9))
                .andExpect(jsonPath("$.data.unsubmittedCount").value(2))
                .andExpect(jsonPath("$.data.scoredCount").value(9))
                .andExpect(jsonPath("$.data.scoreDistribution['0-59']").value(1))
                .andExpect(jsonPath("$.data.scoreDistribution['60-69']").value(2))
                .andExpect(jsonPath("$.data.scoreDistribution['70-79']").value(2))
                .andExpect(jsonPath("$.data.scoreDistribution['80-89']").value(2))
                .andExpect(jsonPath("$.data.scoreDistribution['90-100']").value(2))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[0]").value(610))
                .andExpect(jsonPath("$.data.unsubmittedStudentIds[1]").value(611));
    }

    @Test
    void statisticsReturnsFixedZeroBucketsWhenCurrentRosterIsEmpty() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        insertSubmission(homeworkId, 999, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("100.00"), 1, true, false, "2026-08-22 10:00:00");

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudentCount").value(0))
                .andExpect(jsonPath("$.data.submittedCount").value(0))
                .andExpect(jsonPath("$.data.unsubmittedCount").value(0))
                .andExpect(jsonPath("$.data.scoredCount").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['0-59']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['60-69']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['70-79']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['80-89']").value(0))
                .andExpect(jsonPath("$.data.scoreDistribution['90-100']").value(0));
    }

    @Test
    void statisticsCountsAutomaticEvaluationAndReviewAttentionByContract() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        String submittedAt = "2026-08-22 10:00:00";
        insertSubmission(homeworkId, 601, "OBJECTIVE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, submittedAt);
        insertSubmission(homeworkId, 602, "CODE", "SUBMITTED", "ACCEPTED", "NEED_REVIEW", 80,
                new BigDecimal("90.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 603, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                new BigDecimal("70.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 604, "FILE", "SUBMITTED", "NONE", "REVIEWED", null,
                new BigDecimal("60.00"), 1, true, false, submittedAt);
        insertSubmission(homeworkId, 605, "OBJECTIVE", "LATE", "SYSTEM_ERROR", "UNREVIEWED", 50,
                null, 1, true, false, submittedAt);
        insertSubmission(homeworkId, 606, "CODE", "SUBMITTED", "RUNNING", "REVIEWED", null,
                new BigDecimal("40.00"), 1, true, false, submittedAt);

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603,604,605,606")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedCount").value(6))
                .andExpect(jsonPath("$.data.autoEvaluableCount").value(4))
                .andExpect(jsonPath("$.data.evaluatedCount").value(2))
                .andExpect(jsonPath("$.data.pendingEvaluationCount").value(2))
                .andExpect(jsonPath("$.data.pendingReviewCount").value(3))
                .andExpect(jsonPath("$.data.reviewedCount").value(2))
                .andExpect(jsonPath("$.data.scoredCount").value(5))
                .andExpect(jsonPath("$.data.averageScore").value(62.00))
                .andExpect(jsonPath("$.data.maxScore").value(90))
                .andExpect(jsonPath("$.data.minScore").value(40))
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    void evaluationPendingAttentionUsesOnlyActiveFinalAutomaticSubmissionsWithStablePaging() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        long firstId = insertSubmission(homeworkId, 601, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 10:00:00");
        long secondId = insertSubmission(homeworkId, 602, "OBJECTIVE", "LATE", "NONE", "UNREVIEWED", null,
                null, 1, true, false, "2026-08-22 10:00:00");
        long thirdId = insertSubmission(homeworkId, 603, "CODE", "SUBMITTED", "RUNNING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 09:00:00");
        insertSubmission(homeworkId, 604, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                null, 1, true, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 605, "CODE", "SUBMITTED", "ACCEPTED", "NEED_REVIEW", 80,
                null, 1, true, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 606, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, false, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 607, "CODE", "REJECTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 608, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, true, "2026-08-22 08:00:00");
        insertSubmission(homeworkId, 999, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 08:00:00");

        org.springframework.http.HttpHeaders managerHeaders = teacherHeaders(
                "101", "101", "101:601,602,603,604,605,606,607,608"
        );
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(managerHeaders)
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(managerHeaders)
                        .param("attention", "EVALUATION_PENDING")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list", hasSize(2)))
                .andExpect(jsonPath("$.data.list[0].submissionId").value(secondId))
                .andExpect(jsonPath("$.data.list[1].submissionId").value(firstId));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(managerHeaders)
                        .param("attention", "EVALUATION_PENDING")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].submissionId").value(thirdId));
    }

    @Test
    void reviewPendingAttentionWaitsForAutomaticEvaluationTerminalState() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        long textId = insertSubmission(homeworkId, 601, "TEXT", "SUBMITTED", "NONE", "UNREVIEWED", null,
                null, 1, true, false, "2026-08-22 10:00:00");
        long fileId = insertSubmission(homeworkId, 602, "FILE", "SUBMITTED", "NONE", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 09:00:00");
        long codeId = insertSubmission(homeworkId, 603, "CODE", "SUBMITTED", "ACCEPTED", "UNREVIEWED", 80,
                null, 1, true, false, "2026-08-22 08:00:00");
        long objectiveId = insertSubmission(homeworkId, 604, "OBJECTIVE", "LATE", "WRONG_ANSWER", "NEED_REVIEW", 40,
                null, 1, true, false, "2026-08-22 07:00:00");
        insertSubmission(homeworkId, 605, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-22 06:00:00");
        insertSubmission(homeworkId, 606, "OBJECTIVE", "SUBMITTED", "RUNNING", "UNREVIEWED", null,
                null, 1, true, false, "2026-08-22 05:00:00");
        insertSubmission(homeworkId, 607, "TEXT", "SUBMITTED", "NONE", "REVIEWED", null,
                null, 1, true, false, "2026-08-22 04:00:00");

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601,602,603,604,605,606,607"))
                        .param("attention", "REVIEW_PENDING")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.list", hasSize(4)))
                .andExpect(jsonPath("$.data.list[0].submissionId").value(textId))
                .andExpect(jsonPath("$.data.list[1].submissionId").value(fileId))
                .andExpect(jsonPath("$.data.list[2].submissionId").value(codeId))
                .andExpect(jsonPath("$.data.list[3].submissionId").value(objectiveId));
    }

    @Test
    void statisticsAndAttentionQueuesRequireCourseManagementPermission() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .param("attention", "REVIEW_PENDING"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .headers(teacherHeaders("202", "202")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(teacherHeaders("202", "202"))
                        .param("attention", "EVALUATION_PENDING"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void objectiveHomeworkSubmissionCreatesEvaluationRecordAndResultView() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long submissionId = submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"true\"]}", studentHeaders("101"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passedCases").value(2))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.reevaluation").value(false))
                .andExpect(jsonPath("$.data.feedback", containsString("2 / 2")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_evaluation WHERE submission_id = ? AND status = 'ACCEPTED'",
                Integer.class,
                submissionId
        )).isEqualTo(1);
    }

    @Test
    void codeHomeworkSubmissionEvaluatesIoCasesAndTeacherCanReevaluate() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long submissionId = submitCodeAnswer(homeworkId, "print(input())", "python", studentHeaders("101"));

        awaitEvaluationStatus(submissionId, "ACCEPTED");

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passedCases").value(1))
                .andExpect(jsonPath("$.data.totalCases").value(1))
                .andExpect(jsonPath("$.data.reevaluation").value(false));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "verify fixed judge data"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.reevaluation").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_evaluation WHERE submission_id = ?",
                Integer.class,
                submissionId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT evaluation_type FROM t_hwk_evaluation WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                submissionId
        )).isEqualTo("REJUDGE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM t_hwk_review_log WHERE submission_id = ? AND operation_type = 'REJUDGE'",
                String.class,
                submissionId
        )).isEqualTo("verify fixed judge data");
    }

    @Test
    void codeEvaluationNeverPersistsScoreAboveHomeworkTotal() throws Exception {
        long homeworkId = createHomeworkAndReturnId(Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK score boundary"),
                entry("description", "Keep automatic scores within the homework total."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 50),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", "[\"python\"]"),
                entry("timeLimitMs", 1000),
                entry("memoryLimitKb", 65536),
                entry("outputCompareMode", "EXACT"),
                entry("testCases", List.of(Map.of(
                        "inputData", "1 2",
                        "expectedOutput", "3",
                        "scoreWeight", 100,
                        "hidden", false,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "sortOrder", 1
                )))
        ));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitCodeAnswer(homeworkId, "print(input())", "python", studentHeaders("101"));

        awaitEvaluationStatus(submissionId, "ACCEPTED");

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(50));

        Map<String, Object> persistedScores = jdbcTemplate.queryForMap(
                "SELECT auto_score, final_score FROM t_hwk_submission WHERE id = ?",
                submissionId
        );
        assertThat((BigDecimal) persistedScores.get("auto_score")).isEqualByComparingTo("50.00");
        assertThat((BigDecimal) persistedScores.get("final_score")).isEqualByComparingTo("50.00");
    }

    @Test
    void teacherReevaluationRequiresReason() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"true\"]}", studentHeaders("101"));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"))
                .andExpect(jsonPath("$.message", containsString("reason")));
    }

    @Test
    void courseManagerReadsEvaluationLogsButStudentCannotReadPrivateLogs() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitCodeAnswer(homeworkId, "#FAKE_WRONG\nprint(input())", "python", studentHeaders("101"));

        awaitEvaluationStatus(submissionId, "WRONG_ANSWER");

        String evaluationBody = mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long evaluationId = objectMapper.readTree(evaluationBody).path("data").path("evaluationId").asLong();

        mockMvc.perform(get("/api/v1/evaluations/{evaluationId}/logs", evaluationId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationId").value(evaluationId))
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.runLog", containsString("wrong output")))
                .andExpect(jsonPath("$.data.triggeredBy").doesNotExist());

        mockMvc.perform(get("/api/v1/evaluations/{evaluationId}/logs", evaluationId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void codeHomeworkEvaluationFailurePreservesSubmissionAndRecordsFailedStatus() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long submissionId = submitCodeAnswer(homeworkId, "#FAKE_WRONG\nprint(input())", "python", studentHeaders("101"));

        awaitEvaluationStatus(submissionId, "WRONG_ANSWER");

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.passedCases").value(0))
                .andExpect(jsonPath("$.data.totalCases").value(1))
                .andExpect(jsonPath("$.data.feedback", containsString("wrong answer")));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.autoScore").value(0));
    }

    @Test
    void codeHomeworkWorkerFailurePreservesSubmissionAndRecordsSystemError() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        long submissionId = submitCodeAnswer(
                homeworkId,
                "#FAKE_SYSTEM_ERROR\nprint(input())",
                "python",
                studentHeaders("101")
        );

        awaitEvaluationStatus(submissionId, "SYSTEM_ERROR");

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.data.autoScore").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE id = ?",
                Integer.class,
                submissionId
        )).isEqualTo(1);
    }

    @Test
    void recoveryEvaluatesPersistedCodeSubmissionWhenInitialAfterCommitDispatchWasMissed() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = insertSubmission(
                homeworkId, 601, "CODE", "SUBMITTED", "PENDING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-08-27 08:00:00"
        );
        insertCodeEvaluation(submissionId, homeworkId, 601, "PENDING", "2026-08-27 08:00:00");

        evaluationRecovery.recoverPendingEvaluations();

        awaitEvaluationStatus(submissionId, "ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT evaluation_status FROM t_hwk_submission WHERE id = ?", String.class, submissionId
        )).isEqualTo("ACCEPTED");
    }

    @Test
    void recoveryRequeuesRunningCodeSubmissionLeftByPreviousProcess() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = insertSubmission(
                homeworkId, 601, "CODE", "SUBMITTED", "RUNNING", "NEED_REVIEW", null,
                null, 1, true, false, "2026-01-01 08:00:00"
        );
        insertCodeEvaluation(submissionId, homeworkId, 601, "RUNNING", "2026-01-01 08:00:00");

        evaluationRecovery.recoverAfterRestart();

        awaitEvaluationStatus(submissionId, "ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT evaluation_status FROM t_hwk_submission WHERE id = ?", String.class, submissionId
        )).isEqualTo("ACCEPTED");
    }

    @Test
    void studentEvaluationResultHidesHiddenCaseExpectedOutput() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayloadWithHiddenCase("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitCodeAnswer(homeworkId, "#FAKE_WRONG\nprint(input())", "python", studentHeaders("101"));

        awaitEvaluationStatus(submissionId, "WRONG_ANSWER");

        String studentBody = mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.feedback", containsString("passed 0 / 2 cases")))
                .andExpect(jsonPath("$.data.compileLog").doesNotExist())
                .andExpect(jsonPath("$.data.runLog").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(studentBody).doesNotContain("SECRET_EXPECTED");
        assertThat(studentBody).doesNotContain("wrong output");
    }

    @Test
    void objectiveReevaluationUpdatesSubmissionSummary() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"false\"]}", studentHeaders("101"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.autoScore").value(40));

        jdbcTemplate.update("""
                        UPDATE t_hwk_question
                        SET answer_json = '[\"false\"]'
                        WHERE homework_id = ? AND sort_order = 2
                        """,
                homeworkId
        );

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "answer key corrected"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.reevaluation").value(true));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.autoScore").value(100))
                .andExpect(jsonPath("$.data.finalScore").value(100));
    }

    @Test
    void courseManagerReviewsSubmissionAndReadsReviewAuditLogs() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectivePayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitObjectiveAnswer(homeworkId, "{\"q1\":[\"2\"],\"q2\":[\"false\"]}", studentHeaders("101"));

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", submissionId)
                        .headers(assistantHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", new BigDecimal("68.50"),
                                "finalScore", new BigDecimal("70.25"),
                                "comment", "Reasoning is mostly correct."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.manualScore").value(68.50))
                .andExpect(jsonPath("$.data.finalScore").value(70.25))
                .andExpect(jsonPath("$.data.comment").value("Reasoning is mostly correct."));

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "verify rubric after manual review"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/review-logs", submissionId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].operationType").value("REJUDGE"))
                .andExpect(jsonPath("$.data[0].reason").value("verify rubric after manual review"))
                .andExpect(jsonPath("$.data[1].operationType").value("REVIEW"))
                .andExpect(jsonPath("$.data[1].oldScore").value(40))
                .andExpect(jsonPath("$.data[1].newScore").value(70.25))
                .andExpect(jsonPath("$.data[1].comment").value("Reasoning is mostly correct."))
                .andExpect(jsonPath("$.data[1].operatorId").value(502));
    }

    @Test
    void teacherReviewRejectsScoreOutsideHomeworkTotalScore() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitTextAnswer(homeworkId, "answer awaiting review", studentHeaders("101"));

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 101,
                                "finalScore", 101,
                                "comment", "out of range"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4008"));
    }

    @Test
    void teacherReviewRejectsArchivedHomeworkSubmission() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitTextAnswer(homeworkId, "answer before archive", studentHeaders("101"));
        jdbcTemplate.update("UPDATE t_hwk_homework SET status = 'ARCHIVED' WHERE id = ?", homeworkId);

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", submissionId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 80,
                                "finalScore", 80,
                                "comment", "late edit"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4003"));
    }

    @Test
    void studentCannotReadPrivateReviewLogs() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitTextAnswer(homeworkId, "private answer", studentHeaders("101"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/review-logs", submissionId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void codeHomeworkSubmissionRejectsLanguageOutsideConfiguredAllowlist() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayload("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "codeText", "public class Main {}",
                                "language", "java"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"))
                .andExpect(jsonPath("$.message", containsString("language")));

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "codeText", "print(input())",
                                "language", "python"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.reviewStatus").value("NEED_REVIEW"));
    }

    @Test
    void studentCannotSubmitAgainWhenHomeworkDisallowsResubmit() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload(false, false));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "first answer"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "second answer"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4006"));
    }

    @Test
    void studentCannotSubmitAfterDeadlineWhenLateSubmitIsDisabled() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload(true, false));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE t_hwk_homework SET deadline = '2026-05-01 23:59:59' WHERE id = ?", homeworkId);

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "late answer"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4004"));
    }

    @Test
    void lateSubmissionIsSavedAsLateWhenHomeworkAllowsLateSubmit() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload(true, true));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE t_hwk_homework SET deadline = '2026-05-01 23:59:59' WHERE id = ?", homeworkId);

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "late but allowed"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.submitStatus").value("LATE"));
    }

    @Test
    void nonMemberStudentCannotSubmitHomework() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "not my course"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void emptySubmissionBodyReturnsFormatErrorInsteadOfServerError() throws Exception {
        long homeworkId = createHomeworkAndReturnId(textPayload());
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));
    }

    @Test
    void publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails() throws Exception {
        notificationEventPublisher.failNextPublish();
        long homeworkId = createHomeworkAndReturnId(objectivePayload());

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:7001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("HWK_5003"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertThat(notificationEventPublisher.events()).isEmpty();
    }

    @Test
    void codeHomeworkWithoutTestCasesIsRejectedWhenPublishing() throws Exception {
        long homeworkId = createHomeworkAndReturnId(Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 code draft"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", "[\"java\"]"),
                entry("timeLimitMs", 1000),
                entry("memoryLimitKb", 65536),
                entry("outputCompareMode", "EXACT"),
                entry("testCases", List.of())
        ));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4007"))
                .andExpect(jsonPath("$.message", containsString("test case")));
    }

    @Test
    void teacherSavesCodeHomeworkTestCasesBeforePublish() throws Exception {
        long homeworkId = createHomeworkAndReturnId(Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 code configured"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", "[\"python\"]"),
                entry("timeLimitMs", 2000),
                entry("memoryLimitKb", 131072),
                entry("outputCompareMode", "TRIM"),
                entry("testCases", List.of())
        ));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.languageLimitJson").value("[\"python\"]"))
                .andExpect(jsonPath("$.data.timeLimitMs").value(2000))
                .andExpect(jsonPath("$.data.memoryLimitKb").value(131072))
                .andExpect(jsonPath("$.data.outputCompareMode").value("TRIM"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/test-cases", homeworkId)
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of(
                                        "inputData", "1 2",
                                        "expectedOutput", "3",
                                        "scoreWeight", 100,
                                        "hidden", false,
                                        "timeLimitMs", 1000,
                                        "memoryLimitKb", 65536,
                                        "sortOrder", 1
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testCases", hasSize(1)));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/test-cases", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].inputData").value("1 2"))
                .andExpect(jsonPath("$.data[0].expectedOutput").value("3"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/test-cases", homeworkId)
                        .headers(studentHeaders("101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:7001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private long createHomeworkAndReturnId(Map<String, Object> payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long submitTextAnswer(long homeworkId, String answerText, org.springframework.http.HttpHeaders headers) throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", answerText))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private long submitObjectiveAnswer(long homeworkId, String answerJson, org.springframework.http.HttpHeaders headers) throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerJson", answerJson))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private long submitCodeAnswer(long homeworkId, String codeText, String language, org.springframework.http.HttpHeaders headers) throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "codeText", codeText,
                                "language", language
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private long insertSubmission(
            long homeworkId,
            long studentId,
            String submitType,
            String submitStatus,
            String evaluationStatus,
            String reviewStatus,
            Integer autoScore,
            BigDecimal finalScore,
            int version,
            boolean isFinal,
            boolean deleted,
            String submittedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO t_hwk_submission
                        (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                         submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                         comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at,
                         is_deleted)
                        VALUES (?, ?, ?, 'test answer', NULL, NULL, NULL, ?, ?, ?, ?, NULL, ?, NULL, ?, ?, ?,
                                NULL, NULL, ?, ?, ?)
                        """,
                homeworkId,
                studentId,
                submitType,
                submitStatus,
                evaluationStatus,
                reviewStatus,
                autoScore,
                finalScore,
                version,
                isFinal,
                submittedAt,
                submittedAt,
                submittedAt,
                deleted
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_submission", Long.class);
    }

    private void insertEvaluation(long submissionId, long homeworkId, long studentId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_evaluation
                (submission_id, homework_id, student_id, evaluation_type, status, score, passed_cases, total_cases,
                 time_used_ms, memory_used_kb, feedback, started_at, finished_at, created_at, updated_at)
                VALUES (?, ?, ?, 'AUTO', 'ACCEPTED', 100, 1, 1, 20, 1024, '历史评测结果',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, submissionId, homeworkId, studentId);
    }

    private void insertCodeEvaluation(
            long submissionId,
            long homeworkId,
            long studentId,
            String status,
            String startedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_evaluation
                (submission_id, homework_id, student_id, evaluation_type, status, score, passed_cases, total_cases,
                 time_used_ms, memory_used_kb, feedback, started_at, finished_at, created_at, updated_at)
                VALUES (?, ?, ?, 'CODE_JUDGE', ?, 0, 0, 1, NULL, NULL, 'waiting for evaluation',
                        ?, NULL, ?, ?)
                """, submissionId, homeworkId, studentId, status, startedAt, startedAt, startedAt);
    }

    private void insertReviewLog(long submissionId, long homeworkId, long studentId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_review_log
                (submission_id, homework_id, student_id, operation_type, old_score, new_score,
                 comment, operator_id, reason, created_at)
                VALUES (?, ?, ?, 'REVIEW', NULL, 100, '历史批阅', 501, '保留历史', CURRENT_TIMESTAMP)
                """, submissionId, homeworkId, studentId);
    }

    private Map<String, List<Map<String, Object>>> childRows(long homeworkId) {
        return Map.of(
                "questions", jdbcTemplate.queryForList(
                        "SELECT id, stem, answer_json FROM t_hwk_question WHERE homework_id = ? ORDER BY id",
                        homeworkId
                ),
                "testCases", jdbcTemplate.queryForList(
                        "SELECT id, input_data, expected_output FROM t_hwk_test_case WHERE homework_id = ? ORDER BY id",
                        homeworkId
                ),
                "judgeConfig", jdbcTemplate.queryForList(
                        "SELECT id, language_limit_json, output_compare_mode FROM t_hwk_judge_config WHERE homework_id = ? ORDER BY id",
                        homeworkId
                ),
                "submissions", jdbcTemplate.queryForList(
                        "SELECT id, student_id, answer_text, submit_status FROM t_hwk_submission WHERE homework_id = ? ORDER BY id",
                        homeworkId
                ),
                "evaluations", jdbcTemplate.queryForList(
                        "SELECT id, submission_id, status, feedback FROM t_hwk_evaluation WHERE homework_id = ? ORDER BY id",
                        homeworkId
                ),
                "reviewLogs", jdbcTemplate.queryForList(
                        "SELECT id, submission_id, operation_type, reason FROM t_hwk_review_log WHERE homework_id = ? ORDER BY id",
                        homeworkId
                )
        );
    }

    private Map<String, Object> objectivePayload() {
        return Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 objective draft"),
                entry("description", "Answer the basics."),
                entry("type", "OBJECTIVE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("questions", List.of(
                        Map.of(
                                "questionType", "SINGLE_CHOICE",
                                "stem", "1 + 1 = ?",
                                "optionsJson", "[\"1\",\"2\"]",
                                "answerJson", "[\"2\"]",
                                "score", 40,
                                "sortOrder", 1
                        ),
                        Map.of(
                                "questionType", "JUDGE",
                                "stem", "Java is statically typed.",
                                "optionsJson", "[\"true\",\"false\"]",
                                "answerJson", "[\"true\"]",
                                "score", 60,
                                "sortOrder", 2
                        )
                ))
        );
    }

    private Map<String, Object> textPayload() {
        return textPayload(true, false);
    }

    private Map<String, Object> textPayload(boolean allowResubmit, boolean allowLateSubmit) {
        return Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK02 text homework"),
                entry("description", "Explain your algorithm."),
                entry("type", "TEXT"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", allowResubmit),
                entry("allowLateSubmit", allowLateSubmit),
                entry("showEvaluationBeforePublish", true),
                entry("questions", List.of()),
                entry("testCases", List.of())
        );
    }

    private Map<String, Object> codePayload(String languageLimitJson) {
        return Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK02 code homework"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", languageLimitJson),
                entry("timeLimitMs", 1000),
                entry("memoryLimitKb", 65536),
                entry("outputCompareMode", "EXACT"),
                entry("testCases", List.of(
                        Map.of(
                                "inputData", "1 2",
                                "expectedOutput", "3",
                                "scoreWeight", 100,
                                "hidden", false,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "sortOrder", 1
                        )
                ))
        );
    }

    private Map<String, Object> codePayloadWithHiddenCase(String languageLimitJson) {
        return Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK04 code hidden evaluation"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("languageLimitJson", languageLimitJson),
                entry("timeLimitMs", 1000),
                entry("memoryLimitKb", 65536),
                entry("outputCompareMode", "EXACT"),
                entry("testCases", List.of(
                        Map.of(
                                "inputData", "1 2",
                                "expectedOutput", "3",
                                "scoreWeight", 50,
                                "hidden", false,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "sortOrder", 1
                        ),
                        Map.of(
                                "inputData", "secret input",
                                "expectedOutput", "SECRET_EXPECTED",
                                "scoreWeight", 50,
                                "hidden", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "sortOrder", 2
                        )
                ))
        );
    }

    private void awaitEvaluationStatus(long submissionId, String expectedStatus) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        String actualStatus = null;
        while (System.nanoTime() < deadline) {
            actualStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM t_hwk_evaluation WHERE submission_id = ? ORDER BY id DESC LIMIT 1",
                    String.class,
                    submissionId
            );
            if (expectedStatus.equals(actualStatus)) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(actualStatus).isEqualTo(expectedStatus);
    }

    private String futureDeadline() {
        return LocalDateTime.now().plusDays(30).withNano(0).toString();
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String courseIds, String manageableCourseIds) {
        return teacherHeaders(courseIds, manageableCourseIds, null);
    }

    private org.springframework.http.HttpHeaders teacherHeaders(
            String courseIds,
            String manageableCourseIds,
            String studentIds
    ) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", courseIds);
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        if (studentIds != null) {
            headers.add("X-Course-Student-Ids", studentIds);
        }
        return headers;
    }

    private org.springframework.http.HttpHeaders assistantHeaders(String courseIds, String manageableCourseIds) {
        return assistantHeaders(courseIds, manageableCourseIds, null);
    }

    private org.springframework.http.HttpHeaders assistantHeaders(
            String courseIds,
            String manageableCourseIds,
            String studentIds
    ) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "502");
        headers.add("X-User-Role", "ASSISTANT");
        headers.add("X-Course-Ids", courseIds);
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        if (studentIds != null) {
            headers.add("X-Course-Student-Ids", studentIds);
        }
        return headers;
    }

    private org.springframework.http.HttpHeaders studentHeaders(String courseIds) {
        return studentHeaders(courseIds, "601");
    }

    private org.springframework.http.HttpHeaders studentHeaders(String courseIds, String userId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", userId);
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }

    static final class RecordingNotificationEventPublisher implements NotificationEventPublisher {
        private final List<NotificationEvent> events = new CopyOnWriteArrayList<>();
        private volatile boolean failNextPublish;

        @Override
        public void publish(NotificationEvent event) {
            if (failNextPublish) {
                failNextPublish = false;
                throw new IllegalStateException("notification broker unavailable");
            }
            events.add(event);
        }

        List<NotificationEvent> events() {
            return new ArrayList<>(events);
        }

        void clear() {
            events.clear();
            failNextPublish = false;
        }

        void failNextPublish() {
            failNextPublish = true;
        }
    }
}
