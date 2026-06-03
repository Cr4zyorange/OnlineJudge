package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
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
    private RecordingNotificationEventPublisher notificationEventPublisher;

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
                entry("deadline", "2026-06-30T23:59:59"),
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
                                "fileIds", List.of("file-1"),
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
    void studentEvaluationResultHidesHiddenCaseExpectedOutput() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codePayloadWithHiddenCase("[\"python\"]"));
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:601")))
                .andExpect(status().isOk());
        long submissionId = submitCodeAnswer(homeworkId, "#FAKE_WRONG\nprint(input())", "python", studentHeaders("101"));

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
    void publishKeepsHomeworkPublishedWhenNotificationDeliveryFails() throws Exception {
        notificationEventPublisher.failNextPublish();
        long homeworkId = createHomeworkAndReturnId(objectivePayload());

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("101", "101", "101:7001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void codeHomeworkWithoutTestCasesIsRejectedWhenPublishing() throws Exception {
        long homeworkId = createHomeworkAndReturnId(Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 code draft"),
                entry("description", "Implement addition."),
                entry("type", "CODE"),
                entry("deadline", "2026-06-30T23:59:59"),
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
                entry("deadline", "2026-06-30T23:59:59"),
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

    private Map<String, Object> objectivePayload() {
        return Map.ofEntries(
                entry("courseId", 101),
                entry("chapterId", 11),
                entry("title", "HWK01 objective draft"),
                entry("description", "Answer the basics."),
                entry("type", "OBJECTIVE"),
                entry("deadline", "2026-06-30T23:59:59"),
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
                entry("deadline", "2026-06-30T23:59:59"),
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
                entry("deadline", "2026-06-30T23:59:59"),
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
                entry("deadline", "2026-06-30T23:59:59"),
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
