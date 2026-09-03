package com.onlinejudge.gradeservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #367 API coverage: every Grade service HTTP endpoint gets at least one
 * executable contract test against the frozen error-code contract.  Data uses
 * disposable H2 state only; Course membership and permission decisions are
 * mocked at the integration boundary.
 */
@SpringBootTest(properties = "grade.rabbit.enabled=false")
@AutoConfigureMockMvc
class GradeApiContractTest {
    private static final long COURSE = 101L;
    private static final long TEACHER = 501L;
    private static final KeyPair KEY = keyPair();
    private static final String JWKS = jwks(KEY);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @MockitoBean CoursePermissionClient coursePermissions;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("grade.identity.jwks-trust-bundle", () -> JWKS);
        registry.add("grade.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void cleanAndMockPermissions() {
        for (String table : List.of(
                "grade_event_outbox",
                "grade_source_projection_gap",
                "grade_source_projection",
                "grade_source_deferred_event",
                "grade_source_reconciliation_request",
                "grade_event_inbox",
                "t_grade_analysis_snapshot",
                "t_grade_analysis_source_version",
                "grade_result_trace",
                "grade_rule_version",
                "t_grade_review_request",
                "t_grade_change_log",
                "t_grade_publish_record",
                "t_course_grade_summary",
                "t_grade_record",
                "t_grade_item",
                "t_grade_calculation_batch")) {
            jdbc.update("DELETE FROM " + table);
        }
        when(coursePermissions.canManageCourseGrade(anyLong(), anyLong())).thenReturn(true);
        when(coursePermissions.canManageCourse(anyLong(), anyLong())).thenReturn(true);
        when(coursePermissions.listCourseStudentIds(anyLong())).thenReturn(List.of(601L, 602L, 603L));
        when(coursePermissions.isCourseMember(anyLong(), anyLong())).thenAnswer(invocation -> {
            long userId = invocation.getArgument(1);
            return userId == 601L || userId == 602L || userId == 603L;
        });
        when(coursePermissions.listCourseTeacherIds(anyLong())).thenReturn(List.of(TEACHER));
    }

    @Test
    void gradeItemCreateListUpdateDeleteAndValidateEndpointsRoundTrip() throws Exception {
        Map<String, Object> payload = Map.of(
                "name", "实验一", "sourceType", "LAB", "sourceId", 301,
                "fullScore", "100.00", "weight", "0.40", "includedInFinal", true, "sortOrder", 1);

        String created = mvc.perform(post("/api/v1/courses/{courseId}/grade-items", COURSE)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("实验一"))
                .andReturn().getResponse().getContentAsString();
        long itemId = json.readTree(created).at("/data/id").asLong();

        mvc.perform(get("/api/v1/courses/{courseId}/grade-items", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].id").value(itemId));

        mvc.perform(put("/api/v1/grade-items/{gradeItemId}", itemId)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "实验一-修订", "sourceType", "LAB", "sourceId", 301,
                                "fullScore", "100.00", "weight", "0.50", "includedInFinal", true, "sortOrder", 2, "enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.weight").value(0.50));

        mvc.perform(post("/api/v1/courses/{courseId}/grade-rules/validate", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalIncludedWeight").value(0.50));

        mvc.perform(delete("/api/v1/grade-items/{gradeItemId}", itemId).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void sourceGradeSyncRecalculateAndGradeTableEndpointsWorkFromProjection() throws Exception {
        long labItem = createGradeItem("实验一", "LAB", 301, "0.40");
        long hwkItem = createGradeItem("作业一", "HWK", 401, "0.60");
        seedProjection("LAB", 301, 601L, "90", "SCORED");
        seedProjection("LAB", 301, 602L, "78", "SCORED");
        seedProjection("HWK", 401, 601L, "80", "SCORED");
        seedProjection("HWK", 401, 602L, null, "UNGRADED");

        mvc.perform(post("/api/v1/courses/{courseId}/grades/sync", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.syncedCount").value(3))
                .andExpect(jsonPath("$.data.missingCount").value(2))
                .andExpect(jsonPath("$.data.ungradedCount").value(1));

        mvc.perform(post("/api/v1/courses/{courseId}/grades/recalculate", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.affectedCount").value(3));

        mvc.perform(get("/api/v1/courses/{courseId}/grades?page=1&size=2", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].studentId").value(601))
                .andExpect(jsonPath("$.data.records[0].summary.finalScore").value(84.00));

        mvc.perform(get("/api/v1/courses/{courseId}/grades/students/{studentId}", COURSE, 601).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.records.length()").value(2));

        jdbc.queryForObject("SELECT COUNT(*) FROM t_grade_item WHERE id = ?", Integer.class, labItem);
        jdbc.queryForObject("SELECT COUNT(*) FROM t_grade_item WHERE id = ?", Integer.class, hwkItem);
    }

    @Test
    void publishPublishRecordsAndMyGradesEndpointsReturnPublishedState() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        seedProjection("LAB", 301, 601L, "90", "SCORED");
        seedProjection("HWK", 401, 601L, "80", "SCORED");
        mvc.perform(post("/api/v1/courses/{courseId}/grades/sync", COURSE).headers(teacher()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/courses/{courseId}/grades/publish", COURSE)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_STUDENTS",
                                "studentIds", List.of(601L),
                                "gradeItemIds", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.publishedCount").value(1))
                .andExpect(jsonPath("$.data.notificationStatus").value("SENT"));

        mvc.perform(get("/api/v1/courses/{courseId}/grade-publish-records", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].publishScope").value("PARTIAL_STUDENTS"));

        mvc.perform(get("/api/v1/courses/{courseId}/my-grades", COURSE).headers(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.summary.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    void adjustGradeRecordAndCourseFinalScoreWriteChangeLogs() throws Exception {
        seedGradeFactsForAdjustment();

        String table = mvc.perform(get("/api/v1/courses/{courseId}/grades?studentKeyword=601", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long recordId = json.readTree(table).at("/data/records/0/records/0/id").asLong();
        long summaryId = json.readTree(table).at("/data/records/0/summary/id").asLong();

        mvc.perform(put("/api/v1/grade-records/{recordId}/adjust", recordId)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newScore", "95.00", "reason", "复核修正记录"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.newScore").value(95.00));

        mvc.perform(put("/api/v1/course-grade-summaries/{summaryId}/adjust", summaryId)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newScore", "88.00", "reason", "总评复核修正"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.summaryId").value(summaryId))
                .andExpect(jsonPath("$.data.newScore").value(88.00));

        mvc.perform(get("/api/v1/courses/{courseId}/grade-change-logs?studentId=601", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void gradeAnalysisAndCompletionEndpointsReturnSnapshotCounts() throws Exception {
        seedGradeFactsForAdjustment();

        mvc.perform(get("/api/v1/courses/{courseId}/grade-analysis?targetType=COURSE_TOTAL", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.targetType").value("COURSE_TOTAL"))
                .andExpect(jsonPath("$.data.totalStudentCount").value(3))
                .andExpect(jsonPath("$.data.completedCount").value(1));

        mvc.perform(get("/api/v1/courses/{courseId}/grade-items/{gradeItemId}/completion", COURSE, 1).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.gradeItemId").value(1))
                .andExpect(jsonPath("$.data.totalStudentCount").value(3));
    }

    @Test
    void gradeReviewSubmitListAndProcessEndpointsRoundTrip() throws Exception {
        createGradeItem("实验一", "LAB", 301, "0.40");
        createGradeItem("作业一", "HWK", 401, "0.60");
        seedProjection("LAB", 301, 601L, "90", "SCORED");
        seedProjection("HWK", 401, 601L, "80", "SCORED");
        mvc.perform(post("/api/v1/courses/{courseId}/grades/sync", COURSE).headers(teacher()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/courses/{courseId}/grades/publish", COURSE)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_STUDENTS",
                                "studentIds", List.of(601L),
                                "gradeItemIds", List.of()))))
                .andExpect(status().isOk());

        String submitted = mvc.perform(post("/api/v1/courses/{courseId}/grade-review-requests", COURSE)
                        .headers(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("targetType", "FINAL_SCORE", "reason", "总评漏算补交成绩"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(submitted).at("/data/requestId").asLong();

        mvc.perform(get("/api/v1/courses/{courseId}/my-grade-review-requests", COURSE).headers(student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/v1/courses/{courseId}/grade-review-requests?status=PENDING", COURSE).headers(teacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].requestId").value(requestId));

        mvc.perform(put("/api/v1/grade-review-requests/{requestId}/process", requestId)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "action", "APPROVE", "adjustedScore", "88.00", "responseComment", "确认补交成绩有效"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void readinessProbeReportsUp() throws Exception {
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedGradeApisRejectMissingBearer() throws Exception {
        mvc.perform(get("/api/v1/courses/{courseId}/grade-items", COURSE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotManageGradeRulesOrPublishThroughApi() throws Exception {
        mvc.perform(post("/api/v1/courses/{courseId}/grade-rules/validate", COURSE).headers(student()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        mvc.perform(post("/api/v1/courses/{courseId}/grades/publish", COURSE)
                        .headers(student())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "publishScope", "COURSE", "studentIds", List.of(), "gradeItemIds", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));
    }

    @Test
    void gradeRecordAdjustRejectsBlankReason() throws Exception {
        seedGradeFactsForAdjustment();
        long recordId = jdbc.queryForObject(
                "SELECT id FROM t_grade_record WHERE course_id = ? AND student_id = 601 AND grade_item_id = 1",
                Long.class, COURSE);

        mvc.perform(put("/api/v1/grade-records/{recordId}/adjust", recordId)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newScore", "95.00", "reason", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-GRD-06"));
    }

    @Test
    void publishRejectsPartialItemsScopeUntilItemScopeVisibilityIsImplemented() throws Exception {
        createGradeItem("实验一", "LAB", 301, "1.00");
        seedProjection("LAB", 301, 601L, "90", "SCORED");
        mvc.perform(post("/api/v1/courses/{courseId}/grades/sync", COURSE).headers(teacher()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/courses/{courseId}/grades/publish", COURSE)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "publishScope", "PARTIAL_ITEMS", "studentIds", List.of(), "gradeItemIds", List.of(1L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR-GRD-04"));
    }

    private void seedGradeFactsForAdjustment() {
        jdbc.update("""
                INSERT INTO t_grade_item (id, course_id, name, source_type, source_id, full_score, weight,
                                          included_in_final, enabled, sort_order, created_by)
                VALUES (1, ?, '实验一', 'LAB', 301, 100.00, 0.4000, TRUE, TRUE, 1, ?),
                       (2, ?, '作业一', 'HWK', 401, 100.00, 0.6000, TRUE, TRUE, 2, ?)
                """, COURSE, TEACHER, COURSE, TEACHER);
        jdbc.update("""
                INSERT INTO t_grade_record (id, course_id, student_id, grade_item_id, source_type, source_id,
                                            raw_score, weighted_score, grade_status, publish_status,
                                            source_updated_at, calculated_at, created_at, updated_at)
                VALUES (1, ?, 601, 1, 'LAB', 301, 90.00, 36.00, 'SCORED', 'UNPUBLISHED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                       (2, ?, 601, 2, 'HWK', 401, 80.00, 48.00, 'SCORED', 'UNPUBLISHED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, COURSE, COURSE);
        jdbc.update("""
                INSERT INTO t_course_grade_summary (id, course_id, student_id, final_score, final_status,
                                                    publish_status, calculation_batch_id, created_at, updated_at)
                VALUES (1, ?, 601, 84.00, 'CALCULATED', 'UNPUBLISHED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, COURSE);
    }

    private void seedProjection(String sourceType, long sourceId, long studentId, String score, String status) {
        jdbc.update("""
                INSERT INTO grade_source_projection
                    (aggregate_id, course_id, source_type, source_id, student_id, score, full_score,
                     source_status, source_version, occurred_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 100.00, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                sourceType + ":" + sourceId + ":" + studentId,
                String.valueOf(COURSE),
                sourceType,
                String.valueOf(sourceId),
                String.valueOf(studentId),
                score,
                status);
    }

    private long createGradeItem(String name, String sourceType, long sourceId, String weight) throws Exception {
        String response = mvc.perform(post("/api/v1/courses/{courseId}/grade-items", COURSE)
                        .headers(teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", name, "sourceType", sourceType, "sourceId", sourceId,
                                "fullScore", "100.00", "weight", weight, "includedInFinal", true,
                                "sortOrder", sourceId == 301 ? 1 : 2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).at("/data/id").asLong();
    }

    private HttpHeaders teacher() throws Exception {
        return bearer(token(TEACHER, "TEACHER", "grade:manage"));
    }

    private HttpHeaders student() throws Exception {
        return bearer(token(601L, "STUDENT", "grade:view"));
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private static String token(long userId, String role, String permission) throws Exception {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"RS256\",\"kid\":\"grade-contract\"}");
        String payload = encode("{\"iss\":\"onlinejudge.identity.v2\",\"aud\":\"onlinejudge.api\",\"iat\":" + now
                + ",\"exp\":" + (now + 300) + ",\"userId\":\"" + userId + "\",\"username\":\"u" + userId + "\","
                + "\"roles\":[\"" + role + "\"],\"permissions\":[\"" + permission + "\"],\"securityVersion\":1}");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(KEY.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String jwks(KeyPair pair) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        return "{\"keys\":[{\"kid\":\"grade-contract\",\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + unsigned(publicKey.getModulus()) + "\",\"e\":\"" + unsigned(publicKey.getPublicExponent()) + "\"}]}";
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
