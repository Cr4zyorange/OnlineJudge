package com.onlinejudge.grd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_item_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM t_grade_item",
                "DELETE FROM t_grade_calculation_batch"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class GradeItemControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherCreatesAndListsGradeItemsThroughApi() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "实验一",
                "sourceType", "LAB",
                "sourceId", 301,
                "fullScore", "100.00",
                "weight", "0.40",
                "includedInFinal", true,
                "sortOrder", 1
        );

        mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.name").value("实验一"))
                .andExpect(jsonPath("$.data.sourceType").value("LAB"))
                .andExpect(jsonPath("$.data.includedInFinal").value(true));

        mockMvc.perform(get("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("实验一"));
    }

    @Test
    void teacherUsesDocumentedGradeItemApisForUpdateDeleteAndValidate() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "作业一",
                "sourceType", "HWK",
                "sourceId", 401,
                "fullScore", "100.00",
                "weight", "0.50",
                "includedInFinal", true,
                "sortOrder", 1
        );

        String body = mockMvc.perform(post("/api/v1/courses/202/grade-items")
                        .headers(teacherHeaders("202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long gradeItemId = objectMapper.readTree(body).path("data").path("id").asLong();

        Map<String, Object> updatePayload = Map.of(
                "name", "作业一-修订",
                "sourceType", "HWK",
                "sourceId", 401,
                "fullScore", "100.00",
                "weight", "0.60",
                "includedInFinal", true,
                "sortOrder", 2,
                "enabled", true
        );

        mockMvc.perform(put("/api/v1/grade-items/{gradeItemId}", gradeItemId)
                        .headers(teacherHeaders("202"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("作业一-修订"))
                .andExpect(jsonPath("$.data.weight").value(0.60));

        mockMvc.perform(post("/api/v1/courses/202/grade-rules/validate")
                        .headers(teacherHeaders("202")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalIncludedWeight").value(0.60));

        mockMvc.perform(delete("/api/v1/grade-items/{gradeItemId}", gradeItemId)
                        .headers(teacherHeaders("202")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void teacherValidatesCandidateRulesFromDocumentedRequestBody() throws Exception {
        Map<String, Object> payload = Map.of(
                "gradeItems", java.util.List.of(
                        Map.of(
                                "name", "课堂表现",
                                "sourceType", "OTHER_COURSE_ITEM",
                                "sourceId", 901,
                                "fullScore", "10.00",
                                "weight", "0.20",
                                "includedInFinal", true,
                                "sortOrder", 1
                        ),
                        Map.of(
                                "name", "实验三",
                                "sourceType", "LAB",
                                "sourceId", 303,
                                "fullScore", "100.00",
                                "weight", "0.30",
                                "includedInFinal", true,
                                "sortOrder", 2
                        )
                )
        );

        mockMvc.perform(post("/api/v1/courses/404/grade-rules/validate")
                        .headers(teacherHeaders("404"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalIncludedWeight").value(0.50));
    }

    @Test
    void gradeRuleValidationReturnsDocumentedRuleErrorWhenIncludedWeightExceedsOne() throws Exception {
        Map<String, Object> payload = Map.of(
                "gradeItems", java.util.List.of(
                        Map.of(
                                "name", "实验二",
                                "sourceType", "LAB",
                                "sourceId", 302,
                                "fullScore", "100.00",
                                "weight", "0.70",
                                "includedInFinal", true,
                                "sortOrder", 1
                        ),
                        Map.of(
                                "name", "作业二",
                                "sourceType", "HWK",
                                "sourceId", 402,
                                "fullScore", "100.00",
                                "weight", "0.60",
                                "includedInFinal", true,
                                "sortOrder", 2
                        )
                )
        );

        mockMvc.perform(post("/api/v1/courses/303/grade-rules/validate")
                        .headers(teacherHeaders("303"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]", containsString("计入总评的权重之和不能超过 1")));
    }

    @Test
    void studentCannotCreateGradeItemThroughApi() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "作业一",
                "sourceType", "HWK",
                "sourceId", 401,
                "fullScore", "100.00",
                "weight", "0.50",
                "includedInFinal", true,
                "sortOrder", 1
        );

        mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .header("X-User-Id", "601")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Manageable-Course-Ids", "101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"))
                .andExpect(jsonPath("$.message", containsString("教师无课程成绩管理权限")));
    }

    @Test
    void teacherCannotManageGradeItemsForCourseOutsideCrsPermissionScope() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "作业一",
                "sourceType", "HWK",
                "sourceId", 401,
                "fullScore", "100.00",
                "weight", "0.50",
                "includedInFinal", true,
                "sortOrder", 1
        );

        mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders("202,303"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-GRD-01"))
                .andExpect(jsonPath("$.message", containsString("教师无课程成绩管理权限")));
    }

    @Test
    void teacherCannotPersistGradeItemWhenIncludedWeightWouldExceedOne() throws Exception {
        Map<String, Object> first = Map.of(
                "name", "实验二",
                "sourceType", "LAB",
                "sourceId", 302,
                "fullScore", "100.00",
                "weight", "0.70",
                "includedInFinal", true,
                "sortOrder", 1
        );
        Map<String, Object> second = Map.of(
                "name", "作业二",
                "sourceType", "HWK",
                "sourceId", 402,
                "fullScore", "100.00",
                "weight", "0.40",
                "includedInFinal", true,
                "sortOrder", 2
        );

        mockMvc.perform(post("/api/v1/courses/505/grade-items")
                        .headers(teacherHeaders("505"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/courses/505/grade-items")
                        .headers(teacherHeaders("505"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-GRD-03"))
                .andExpect(jsonPath("$.message", containsString("计入总评的权重之和不能超过 1")));
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String manageableCourseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        return headers;
    }
}
