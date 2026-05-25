package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
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
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                        .headers(teacherHeaders("202", "202")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

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
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));

        mockMvc.perform(post("/api/v1/courses/303/labs")
                        .headers(teacherHeaders("202", "202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"))
                .andExpect(jsonPath("$.message", containsString("无课程管理权限")));
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String courseIds, String manageableCourseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", courseIds);
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        return headers;
    }

    private org.springframework.http.HttpHeaders studentHeaders(String courseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "601");
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }
}
