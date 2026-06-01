package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        statements = {
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
                .andExpect(jsonPath("$.data.languageLimitJson").doesNotExist())
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
    void objectiveHomeworkSubmissionIsScoredAndMarkedReviewed() throws Exception {
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
                .andExpect(jsonPath("$.data.finalScore").value(100));
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
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "601");
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
