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
        scripts = "file:../database/migrations/20260527_01_create_hwk_homework.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = {
                "DELETE FROM t_hwk_evaluation",
                "DELETE FROM t_hwk_submission",
                "DELETE FROM t_hwk_test_case",
                "DELETE FROM t_hwk_question",
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
    void teacherCreatesDraftHomeworkWithQuestionsAndTestCasesThroughDocumentedApi() throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectiveHomeworkPayload(101L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.title").value("作业一"))
                .andExpect(jsonPath("$.data.type").value("OBJECTIVE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.questions", hasSize(2)))
                .andExpect(jsonPath("$.data.questions[0].answerJson").exists())
                .andExpect(jsonPath("$.data.testCases", hasSize(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long homeworkId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", homeworkId)
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(homeworkId))
                .andExpect(jsonPath("$.data.questions", hasSize(2)));
    }

    @Test
    void teacherPublishesConfiguredHomeworkAndNotifiesCourseStudents() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codeHomeworkPayload(202L), teacherHeaders("202"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("202", "7101,7102")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        assertThat(notificationEventPublisher.events()).hasSize(1);
        NotificationEvent event = notificationEventPublisher.events().get(0);
        assertThat(event.type()).isEqualTo("HOMEWORK_PUBLISHED");
        assertThat(event.courseId()).isEqualTo(202L);
        assertThat(event.targetId()).isEqualTo(homeworkId);
        assertThat(event.recipientUserIds()).containsExactly(7101L, 7102L);
    }

    @Test
    void teacherCannotPublishCodeHomeworkWithoutTestCases() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codeHomeworkPayloadWithoutTestCases(303L), teacherHeaders("303"));

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(teacherHeaders("303")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-HWK-03"))
                .andExpect(jsonPath("$.message", containsString("代码作业至少配置一个测试用例")));
    }

    @Test
    void studentCannotCreateHomeworkAndTeacherCannotManageUnauthorizedCourse() throws Exception {
        mockMvc.perform(post("/api/v1/homeworks")
                        .headers(studentHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectiveHomeworkPayload(101L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));

        mockMvc.perform(post("/api/v1/homeworks")
                        .headers(teacherHeaders("202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectiveHomeworkPayload(101L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-HWK-01"))
                .andExpect(jsonPath("$.message", containsString("无课程作业管理权限")));
    }

    @Test
    void studentViewsOnlyPublishedCourseHomeworkWithoutAnswersOrHiddenTestCases() throws Exception {
        long objectiveHomeworkId = createHomeworkAndReturnId(objectiveHomeworkPayload(404L), teacherHeaders("404"));
        publishHomework(objectiveHomeworkId, teacherHeaders("404"));
        long codeHomeworkId = createHomeworkAndReturnId(codeHomeworkPayloadWithHiddenTestCase(404L), teacherHeaders("404"));
        publishHomework(codeHomeworkId, teacherHeaders("404"));

        mockMvc.perform(get("/api/v1/homeworks")
                        .param("courseId", "404")
                        .headers(studentHeaders("404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", objectiveHomeworkId)
                        .headers(studentHeaders("404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].answerJson").doesNotExist());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", codeHomeworkId)
                        .headers(studentHeaders("404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testCases[0].inputData").doesNotExist())
                .andExpect(jsonPath("$.data.testCases[0].expectedOutput").doesNotExist());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}", objectiveHomeworkId)
                        .headers(studentHeaders("405")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-HWK-01"));
    }

    @Test
    void studentSubmitsHomeworkWithIdentityFromCurrentUserAndReadsOwnHistory() throws Exception {
        long homeworkId = createHomeworkAndReturnId(fileHomeworkPayload(505L, true, false), teacherHeaders("505"));
        publishHomework(homeworkId, teacherHeaders("505"));

        Map<String, Object> payload = Map.of(
                "studentId", 9999,
                "answerText", "report summary",
                "fileUrl", "/uploads/hwk/report.pdf"
        );

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("505"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.homeworkId").value(homeworkId))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.submitType").value("FILE"))
                .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.data.isFinal").value(true))
                .andExpect(jsonPath("$.data.submittedAt").exists());

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/my-submissions", homeworkId)
                        .headers(studentHeaders("505")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentId").value(601))
                .andExpect(jsonPath("$.data[0].answerText").value("report summary"));
    }

    @Test
    void studentSubmissionHonorsResubmitAndDeadlineRules() throws Exception {
        long noResubmitHomeworkId = createHomeworkAndReturnId(fileHomeworkPayload(606L, false, false), teacherHeaders("606"));
        publishHomework(noResubmitHomeworkId, teacherHeaders("606"));

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", noResubmitHomeworkId)
                        .headers(studentHeaders("606"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileUrl", "/uploads/hwk/once.pdf"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", noResubmitHomeworkId)
                        .headers(studentHeaders("606"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileUrl", "/uploads/hwk/twice.pdf"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-HWK-05"))
                .andExpect(jsonPath("$.message", containsString("不允许重复提交")));

        long closedDeadlineHomeworkId = createHomeworkAndReturnId(fileHomeworkPayload(707L, true, false), teacherHeaders("707"));
        publishHomework(closedDeadlineHomeworkId, teacherHeaders("707"));
        jdbcTemplate.update("UPDATE t_hwk_homework SET deadline = '2026-01-01 00:00:00' WHERE id = ?", closedDeadlineHomeworkId);

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", closedDeadlineHomeworkId)
                        .headers(studentHeaders("707"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileUrl", "/uploads/hwk/late.pdf"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-HWK-06"))
                .andExpect(jsonPath("$.message", containsString("已截止")));
    }

    @Test
    void submissionHistoryKeepsEveryVersionAndMarksLatestAndEffectiveSubmissions() throws Exception {
        long homeworkId = createHomeworkAndReturnId(fileHomeworkPayload(808L, true, false), teacherHeaders("808"));
        publishHomework(homeworkId, teacherHeaders("808"));

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("808"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "first version"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isLatest").value(true))
                .andExpect(jsonPath("$.data.isFinal").value(true));

        String secondBody = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("808"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "second version"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isLatest").value(true))
                .andExpect(jsonPath("$.data.isFinal").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long secondSubmissionId = objectMapper.readTree(secondBody).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/my-submissions", homeworkId)
                        .headers(studentHeaders("808")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].answerText").value("second version"))
                .andExpect(jsonPath("$.data[0].isLatest").value(true))
                .andExpect(jsonPath("$.data[0].isFinal").value(true))
                .andExpect(jsonPath("$.data[1].answerText").value("first version"))
                .andExpect(jsonPath("$.data[1].isLatest").value(false))
                .andExpect(jsonPath("$.data[1].isFinal").value(false));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(teacherHeaders("808")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].studentId").value(601))
                .andExpect(jsonPath("$.data[0].isLatest").value(true));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", secondSubmissionId)
                        .headers(teacherHeaders("808")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(secondSubmissionId))
                .andExpect(jsonPath("$.data.answerText").value("second version"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}", secondSubmissionId)
                        .headers(otherStudentHeaders("808")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-HWK-01"));
    }

    @Test
    void objectiveHomeworkSubmissionIsAutomaticallyScoredAndEvaluationCanBeQueried() throws Exception {
        long homeworkId = createHomeworkAndReturnId(objectiveHomeworkPayload(909L), teacherHeaders("909"));
        publishHomework(homeworkId, teacherHeaders("909"));

        String body = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("909"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerJson", "{\"1\":[\"main\"],\"2\":[\"true\"]}"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.autoScore").value(100.00))
                .andExpect(jsonPath("$.data.finalScore").value(100.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/evaluation", submissionId)
                        .headers(studentHeaders("909")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.passedCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void codeHomeworkSubmissionRunsBasicIoComparisonAndTeacherCanReevaluate() throws Exception {
        long homeworkId = createHomeworkAndReturnId(codeHomeworkPayload(1001L), teacherHeaders("1001"));
        publishHomework(homeworkId, teacherHeaders("1001"));

        String body = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .headers(studentHeaders("1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "codeText", "3",
                                "language", "OUTPUT"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.autoScore").value(100.00))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/reevaluate", submissionId)
                        .headers(teacherHeaders("1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.message", containsString("IO")));
    }

    private long createHomeworkAndReturnId(Map<String, Object> payload, org.springframework.http.HttpHeaders headers)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/homeworks")
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private void publishHomework(long homeworkId, org.springframework.http.HttpHeaders headers) throws Exception {
        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private Map<String, Object> objectiveHomeworkPayload(long courseId) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 11),
                entry("title", "作业一"),
                entry("description", "完成基础选择题"),
                entry("type", "OBJECTIVE"),
                entry("totalScore", "100.00"),
                entry("deadline", "2026-07-01T23:59:59"),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", false),
                entry("questions", List.of(
                        Map.of(
                                "questionType", "SINGLE_CHOICE",
                                "stem", "Java 入口方法是？",
                                "optionsJson", "[\"main\",\"start\"]",
                                "answerJson", "[\"main\"]",
                                "score", "50.00",
                                "sortOrder", 1
                        ),
                        Map.of(
                                "questionType", "TRUE_FALSE",
                                "stem", "HTTP 是无状态协议。",
                                "optionsJson", "[\"true\",\"false\"]",
                                "answerJson", "[\"true\"]",
                                "score", "50.00",
                                "sortOrder", 2
                        )
                )),
                entry("testCases", List.of())
        );
    }

    private Map<String, Object> codeHomeworkPayload(long courseId) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(codeHomeworkPayloadWithoutTestCases(courseId));
        payload.put("testCases", List.of(
                Map.of(
                        "inputData", "1 2",
                        "expectedOutput", "3",
                        "scoreWeight", "100.00",
                        "hidden", false,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "sortOrder", 1
                )
        ));
        return payload;
    }

    private Map<String, Object> codeHomeworkPayloadWithHiddenTestCase(long courseId) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(codeHomeworkPayloadWithoutTestCases(courseId));
        payload.put("testCases", List.of(
                Map.of(
                        "inputData", "secret input",
                        "expectedOutput", "secret output",
                        "scoreWeight", "100.00",
                        "hidden", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "sortOrder", 1
                )
        ));
        return payload;
    }

    private Map<String, Object> fileHomeworkPayload(long courseId, boolean allowResubmit, boolean allowLateSubmit) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 33),
                entry("title", "文件作业一"),
                entry("description", "提交课程报告"),
                entry("type", "FILE"),
                entry("totalScore", "100.00"),
                entry("deadline", "2026-07-20T23:59:59"),
                entry("allowResubmit", allowResubmit),
                entry("allowLateSubmit", allowLateSubmit),
                entry("showEvaluationBeforePublish", false),
                entry("questions", List.of()),
                entry("testCases", List.of())
        );
    }

    private Map<String, Object> codeHomeworkPayloadWithoutTestCases(long courseId) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 22),
                entry("title", "代码作业一"),
                entry("description", "实现 A+B"),
                entry("type", "CODE"),
                entry("totalScore", "100.00"),
                entry("deadline", "2026-07-10T23:59:59"),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("questions", List.of()),
                entry("testCases", List.of())
        );
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String manageableCourseIds) {
        return teacherHeaders(manageableCourseIds, null);
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String manageableCourseIds, String studentIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
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

    private org.springframework.http.HttpHeaders otherStudentHeaders(String courseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "602");
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }

    static final class RecordingNotificationEventPublisher implements NotificationEventPublisher {
        private final List<NotificationEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(NotificationEvent event) {
            events.add(event);
        }

        List<NotificationEvent> events() {
            return new ArrayList<>(events);
        }

        void clear() {
            events.clear();
        }
    }
}
