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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_record_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM t_course_grade_summary",
                "DELETE FROM t_grade_record",
                "DELETE FROM t_grade_item",
                "DELETE FROM t_grade_calculation_batch"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class GradeRecordControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherSyncsSourceGradesRecalculatesAndQueriesCourseGradeTableThroughApi() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");

        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.syncedCount").value(3))
                .andExpect(jsonPath("$.data.ungradedCount").value(1))
                .andExpect(jsonPath("$.data.affectedStudentCount").value(2));

        mockMvc.perform(get("/api/v1/courses/101/grades")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].studentId").value(601))
                .andExpect(jsonPath("$.data[0].summary.finalScore").value(84.00))
                .andExpect(jsonPath("$.data[0].summary.finalStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.data[1].studentId").value(602))
                .andExpect(jsonPath("$.data[1].summary.finalScore", nullValue()))
                .andExpect(jsonPath("$.data[1].summary.finalStatus").value("INCOMPLETE"));
    }

    @Test
    void studentCannotSyncCourseGradesThroughApi() throws Exception {
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .header("X-User-Id", "601")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Course-Ids", "101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));
    }

    private void createGradeItem(String name, String sourceType, long sourceId, String weight) throws Exception {
        Map<String, Object> payload = Map.of(
                "name", name,
                "sourceType", sourceType,
                "sourceId", sourceId,
                "fullScore", "100.00",
                "weight", weight,
                "includedInFinal", true,
                "sortOrder", sourceId == 301 ? 1 : 2
        );

        mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    private org.springframework.http.HttpHeaders teacherHeaders(String manageableCourseIds) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        return headers;
    }
}
