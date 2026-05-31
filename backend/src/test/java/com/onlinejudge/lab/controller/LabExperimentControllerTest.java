package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_experiment_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM lab_testcase",
                "DELETE FROM lab_experiment"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class LabExperimentControllerTest {
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
    private RecordingNotificationEventPublisher notificationEventPublisher;

    @BeforeEach
    void clearNotificationEvents() {
        notificationEventPublisher.clear();
    }

    @Test
    void teacherCreatesListsAndReadsLabThroughDocumentedApis() throws Exception {
        Map<String, Object> payload = Map.ofEntries(
                entry("title", "实验一"),
                entry("description", "实现链表基本操作"),
                entry("deadline", "2026-06-30T23:59:59"),
                entry("maxScore", 100),
                entry("attachmentIds", List.of(11, 12)),
                entry("allowedLanguages", "java,python"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of(
                        Map.of(
                                "input", "1 2",
                                "expectedOutput", "3",
                                "scoreWeight", 50,
                                "public", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 1
                        ),
                        Map.of(
                                "input", "2 3",
                                "expectedOutput", "5",
                                "scoreWeight", 50,
                                "public", false,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 2
                        )
                ))
        );

        String body = mockMvc.perform(post("/api/v1/courses/101/labs")
                        .headers(teacherHeaders("101", "101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.title").value("实验一"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.testcases", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long labId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/courses/101/labs")
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("实验一"));

        mockMvc.perform(get("/api/v1/labs/{labId}", labId)
                        .headers(teacherHeaders("101", "101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(labId))
                .andExpect(jsonPath("$.data.testcases", hasSize(2)))
                .andExpect(jsonPath("$.data.attachmentIds", hasSize(2)));
    }

    @Test
    void studentCourseMemberCanReadPublishedLabsButCannotSeeHiddenExpectedOutput() throws Exception {
        long labId = createLabAndReturnId(404L, teacherHeaders("404", "404"), Map.ofEntries(
                entry("title", "学生可见实验"),
                entry("description", "用于验证学生侧读取"),
                entry("deadline", "2026-07-05T23:59:59"),
                entry("maxScore", 100),
                entry("attachmentIds", List.of(31, 32)),
                entry("allowedLanguages", "java,python"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of(
                        Map.of(
                                "input", "1 1",
                                "expectedOutput", "2",
                                "scoreWeight", 40,
                                "public", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 1
                        ),
                        Map.of(
                                "input", "2 2",
                                "expectedOutput", "4",
                                "scoreWeight", 60,
                                "public", false,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 2
                        )
                ))
        ));

        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .headers(teacherHeaders("404", "404", "7001,7002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/courses/404/labs")
                        .headers(studentHeaders("404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("学生可见实验"));

        mockMvc.perform(get("/api/v1/labs/{labId}", labId)
                        .headers(studentHeaders("404")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(labId))
                .andExpect(jsonPath("$.data.testcases", hasSize(2)))
                .andExpect(jsonPath("$.data.testcases[0].expectedOutput").value("2"))
                .andExpect(jsonPath("$.data.testcases[1].public").value(false))
                .andExpect(jsonPath("$.data.testcases[1].expectedOutput").doesNotExist());
    }

    @Test
    void studentCannotReadDraftLabEvenAsCourseMember() throws Exception {
        long labId = createLabAndReturnId(405L, teacherHeaders("405", "405"), Map.ofEntries(
                entry("title", "草稿实验"),
                entry("description", "学生不应看到"),
                entry("deadline", "2026-07-05T23:59:59"),
                entry("maxScore", 100),
                entry("attachmentIds", List.of()),
                entry("allowedLanguages", "java"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of())
        ));

        mockMvc.perform(get("/api/v1/courses/405/labs")
                        .headers(studentHeaders("405")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/v1/labs/{labId}", labId)
                        .headers(studentHeaders("405")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));
    }

    @Test
    void teacherUpdatesPublishesClosesAndDeletesDraftLab() throws Exception {
        Map<String, Object> payload = Map.ofEntries(
                entry("title", "实验二"),
                entry("description", "初版"),
                entry("deadline", "2026-06-20T23:59:59"),
                entry("maxScore", 100),
                entry("attachmentIds", List.of()),
                entry("allowedLanguages", "java"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of())
        );

        String firstBody = mockMvc.perform(post("/api/v1/courses/202/labs")
                        .headers(teacherHeaders("202", "202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long firstLabId = objectMapper.readTree(firstBody).path("data").path("id").asLong();

        Map<String, Object> updatePayload = Map.ofEntries(
                entry("title", "实验二-修订"),
                entry("description", "更新后的说明"),
                entry("deadline", "2026-06-25T23:59:59"),
                entry("maxScore", 120),
                entry("attachmentIds", List.of(21)),
                entry("allowedLanguages", "java,cpp"),
                entry("evaluationMode", "MIXED"),
                entry("autoEvaluate", false),
                entry("reportRequired", true),
                entry("timeLimitMs", 90000),
                entry("memoryLimitKb", 524288),
                entry("testcases", List.of(
                        Map.of(
                                "input", "3 4",
                                "expectedOutput", "7",
                                "scoreWeight", 100,
                                "public", true,
                                "timeLimitMs", 2000,
                                "memoryLimitKb", 65536,
                                "orderNum", 1
                        )
                ))
        );

        mockMvc.perform(put("/api/v1/labs/{labId}", firstLabId)
                        .headers(teacherHeaders("202", "202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("实验二-修订"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.testcases", hasSize(1)));

        mockMvc.perform(post("/api/v1/labs/{labId}/publish", firstLabId)
                        .headers(teacherHeaders("202", "202", "8101,8102,8103")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        assertThat(notificationEventPublisher.events()).hasSize(1);
        NotificationEvent publishedEvent = notificationEventPublisher.events().get(0);
        assertThat(publishedEvent.courseId()).isEqualTo(202L);
        assertThat(publishedEvent.recipientUserIds()).containsExactly(8101L, 8102L, 8103L);
        assertThat(publishedEvent.type()).isEqualTo("LAB_EXPERIMENT_PUBLISHED");
        assertThat(publishedEvent.targetId()).isEqualTo(firstLabId);

        mockMvc.perform(post("/api/v1/labs/{labId}/close", firstLabId)
                        .headers(teacherHeaders("202", "202")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        String secondBody = mockMvc.perform(post("/api/v1/courses/202/labs")
                        .headers(teacherHeaders("202", "202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long draftLabId = objectMapper.readTree(secondBody).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/v1/labs/{labId}", draftLabId)
                        .headers(teacherHeaders("202", "202")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void controllerRejectsInvalidPayloadAndPermissionViolations() throws Exception {
        Map<String, Object> invalidPayload = Map.ofEntries(
                entry("title", ""),
                entry("description", "无效请求"),
                entry("deadline", "2020-01-01T00:00:00"),
                entry("maxScore", 0),
                entry("attachmentIds", List.of()),
                entry("allowedLanguages", "java"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 0),
                entry("memoryLimitKb", 0),
                entry("testcases", List.of())
        );

        mockMvc.perform(post("/api/v1/courses/303/labs")
                        .headers(teacherHeaders("303", "303"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest());

        Map<String, Object> validPayload = Map.ofEntries(
                entry("title", "实验三"),
                entry("description", "权限测试"),
                entry("deadline", "2026-07-01T23:59:59"),
                entry("maxScore", 100),
                entry("attachmentIds", List.of()),
                entry("allowedLanguages", "java"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", true),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of())
        );

        mockMvc.perform(post("/api/v1/courses/303/labs")
                        .headers(studentHeaders("303"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        mockMvc.perform(post("/api/v1/courses/303/labs")
                        .headers(teacherHeaders("202", "202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"))
                .andExpect(jsonPath("$.message", containsString("无课程管理权限")));
    }

    private long createLabAndReturnId(long courseId, org.springframework.http.HttpHeaders headers, Map<String, Object> payload)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/courses/{courseId}/labs", courseId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
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

    private org.springframework.http.HttpHeaders studentHeaders(String courseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "601");
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
