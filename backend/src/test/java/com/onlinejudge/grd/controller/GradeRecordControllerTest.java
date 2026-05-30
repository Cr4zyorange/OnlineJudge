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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:grade_record_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM t_course_grade_summary",
                "DELETE FROM t_grade_change_log",
                "DELETE FROM t_grade_publish_record",
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
                .andExpect(jsonPath("$.data.calculationBatchId").isNumber())
                .andExpect(jsonPath("$.data.syncedCount").value(3))
                .andExpect(jsonPath("$.data.ungradedCount").value(1))
                .andExpect(jsonPath("$.data.missingCount").value(2))
                .andExpect(jsonPath("$.data.affectedStudentCount").value(3));

        mockMvc.perform(get("/api/v1/courses/101/grades?page=1&size=2")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].studentId").value(601))
                .andExpect(jsonPath("$.data.records[0].summary.finalScore").value(84.00))
                .andExpect(jsonPath("$.data.records[0].summary.finalStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.data.records[1].studentId").value(602))
                .andExpect(jsonPath("$.data.records[1].summary.finalScore", nullValue()))
                .andExpect(jsonPath("$.data.records[1].summary.finalStatus").value("INCOMPLETE"));

        mockMvc.perform(get("/api/v1/courses/101/grades?gradeStatus=MISSING&studentKeyword=603&page=1&size=10")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].studentId").value(603))
                .andExpect(jsonPath("$.data.records[0].records", hasSize(2)));
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

    @Test
    void teacherAdjustsGradeRecordWithReasonAndQueriesChangeLogsThroughApi() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk());

        String tableJson = mockMvc.perform(get("/api/v1/courses/101/grades?studentKeyword=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long recordId = objectMapper.readTree(tableJson)
                .at("/data/records/0/records/0/id")
                .asLong();

        mockMvc.perform(put("/api/v1/grade-records/{recordId}/adjust", recordId)
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newScore", "95.00",
                                "reason", "复核测试用例后修正"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.oldScore").value(90.00))
                .andExpect(jsonPath("$.data.newScore").value(95.00))
                .andExpect(jsonPath("$.data.reason").value("复核测试用例后修正"));

        mockMvc.perform(get("/api/v1/courses/101/grades/students/601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].rawScore").value(95.00))
                .andExpect(jsonPath("$.data.records[0].gradeStatus").value("ADJUSTED"));

        mockMvc.perform(get("/api/v1/courses/101/grade-change-logs?studentId=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].gradeItemId").value(1))
                .andExpect(jsonPath("$.data.records[0].changeType").value("RECORD_ADJUST"))
                .andExpect(jsonPath("$.data.records[0].oldValue").value(90.00))
                .andExpect(jsonPath("$.data.records[0].newValue").value(95.00))
                .andExpect(jsonPath("$.data.records[0].reason").value("复核测试用例后修正"))
                .andExpect(jsonPath("$.data.records[0].operatorId").value(501));
    }

    @Test
    void teacherCannotAdjustGradeRecordWithoutReason() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk());
        String tableJson = mockMvc.perform(get("/api/v1/courses/101/grades?studentKeyword=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long recordId = objectMapper.readTree(tableJson)
                .at("/data/records/0/records/0/id")
                .asLong();

        mockMvc.perform(put("/api/v1/grade-records/{recordId}/adjust", recordId)
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newScore", "95.00",
                                "reason", " "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-GRD-06"));
    }

    @Test
    void teacherAdjustsCourseFinalScoreWithReasonAndKeepsChangeLog() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk());
        String tableJson = mockMvc.perform(get("/api/v1/courses/101/grades?studentKeyword=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long summaryId = objectMapper.readTree(tableJson)
                .at("/data/records/0/summary/id")
                .asLong();

        mockMvc.perform(put("/api/v1/course-grade-summaries/{summaryId}/adjust", summaryId)
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newScore", "88.00",
                                "reason", "课程总评复核修正"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.summaryId").value(summaryId))
                .andExpect(jsonPath("$.data.oldScore").value(84.00))
                .andExpect(jsonPath("$.data.newScore").value(88.00))
                .andExpect(jsonPath("$.data.reason").value("课程总评复核修正"));

        mockMvc.perform(get("/api/v1/courses/101/grades?studentKeyword=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].summary.finalScore").value(88.00))
                .andExpect(jsonPath("$.data.records[0].summary.finalStatus").value("ADJUSTED"));

        mockMvc.perform(get("/api/v1/courses/101/grade-change-logs?studentId=601")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].gradeItemId", nullValue()))
                .andExpect(jsonPath("$.data.records[0].changeType").value("FINAL_ADJUST"))
                .andExpect(jsonPath("$.data.records[0].oldValue").value(84.00))
                .andExpect(jsonPath("$.data.records[0].newValue").value(88.00))
                .andExpect(jsonPath("$.data.records[0].reason").value("课程总评复核修正"));
    }

    @Test
    void teacherPublishesSelectedStudentGradesThenStudentCanQueryPublishedResultThroughApi() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/101/grades/publish")
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_STUDENTS",
                                "studentIds", java.util.List.of(601),
                                "gradeItemIds", java.util.List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.publishId").isNumber())
                .andExpect(jsonPath("$.data.publishedCount").value(1))
                .andExpect(jsonPath("$.data.notificationStatus").value("SENT"));

        mockMvc.perform(get("/api/v1/courses/101/my-grades")
                        .header("X-User-Id", "601")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Course-Ids", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.summary.finalScore").value(84.00))
                .andExpect(jsonPath("$.data.summary.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.records[0].publishStatus").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/courses/101/grade-publish-records")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].publishScope").value("PARTIAL_STUDENTS"))
                .andExpect(jsonPath("$.data.records[0].publishedBy").value(501))
                .andExpect(jsonPath("$.data.records[0].notificationStatus").value("SENT"));
    }

    @Test
    void teacherCannotQueryStudentMyGradesEndpointThroughApi() throws Exception {
        mockMvc.perform(get("/api/v1/courses/101/my-grades")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER")
                        .header("X-Course-Ids", "101")
                        .header("X-Manageable-Course-Ids", "101"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-03"));
    }

    @Test
    void teacherCannotPublishPartialItemsUntilItemScopeVisibilityIsImplemented() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        mockMvc.perform(post("/api/v1/courses/101/grades/sync")
                        .headers(teacherHeaders("101")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/101/grades/publish")
                        .headers(teacherHeaders("101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_ITEMS",
                                "studentIds", java.util.List.of(),
                                "gradeItemIds", java.util.List.of(1)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-GRD-04"))
                .andExpect(jsonPath("$.message").value("部分成绩项发布暂未实现，不能提前公开课程总评"));
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
        headers.add("X-Course-Student-Ids", "101:601,602,603");
        return headers;
    }
}
