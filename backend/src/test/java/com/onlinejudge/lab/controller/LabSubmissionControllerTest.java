package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_submission_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class LabSubmissionControllerTest {
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        deleteIfExists("DELETE FROM lab_submission");
        deleteIfExists("DELETE FROM lab_testcase");
        deleteIfExists("DELETE FROM lab_experiment");
    }

    @Test
    void studentCanSubmitCodeTwiceAndVersionIncrements() throws Exception {
        long labId = createPublishedLab(501L, true, LocalDateTime.now().plusDays(3));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("501"))
                        .param("code", "print('hello')")
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.labId").value(labId))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("501"))
                        .param("code", "print('hello again')")
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2));

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_submission WHERE lab_id = ? AND student_id = ?",
                Integer.class,
                labId,
                601L
        );
        Integer finalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_submission WHERE lab_id = ? AND student_id = ? AND is_final = TRUE",
                Integer.class,
                labId,
                601L
        );

        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(finalCount).isEqualTo(1);
    }

    @Test
    void studentCanSubmitSourceFileWhenCourseMember() throws Exception {
        long labId = createPublishedLab(502L, false, LocalDateTime.now().plusDays(3));

        MockMultipartFile sourceFile = new MockMultipartFile(
                "file",
                "main.py",
                "text/x-python",
                "print('from file')".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file(sourceFile)
                        .headers(studentHeaders("502"))
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submitStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.evaluationStatus").value("NONE"))
                .andExpect(jsonPath("$.data.version").value(1));

        String storedFileId = jdbcTemplate.queryForObject(
                "SELECT file_id FROM lab_submission WHERE lab_id = ? AND student_id = ?",
                String.class,
                labId,
                601L
        );
        String storedCode = jdbcTemplate.queryForObject(
                "SELECT code_content FROM lab_submission WHERE lab_id = ? AND student_id = ?",
                String.class,
                labId,
                601L
        );

        org.assertj.core.api.Assertions.assertThat(storedFileId).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(storedCode).isNull();
    }

    @Test
    void submissionRejectsMissingContentUnsupportedLanguageAndExpiredLab() throws Exception {
        long publishedLabId = createPublishedLab(503L, true, LocalDateTime.now().plusDays(3));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", publishedLabId)
                        .headers(studentHeaders("503"))
                        .param("language", "java"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-03"))
                .andExpect(jsonPath("$.message", containsString("提交代码不能为空且必须上传文件")));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", publishedLabId)
                        .headers(studentHeaders("503"))
                        .param("code", "print('bad language')")
                        .param("language", "ruby"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-04"));

        long expiredLabId = createPublishedLab(504L, true, LocalDateTime.now().plusDays(1));
        jdbcTemplate.update("UPDATE lab_experiment SET deadline = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)),
                expiredLabId);

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", expiredLabId)
                        .headers(studentHeaders("504"))
                        .param("code", "print('late')")
                        .param("language", "python"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAB-409-01"));
    }

    @Test
    void nonCourseMemberCannotSubmitPublishedLab() throws Exception {
        long labId = createPublishedLab(505L, true, LocalDateTime.now().plusDays(3));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("999"))
                        .param("code", "print('forbidden')")
                        .param("language", "python"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"))
                .andExpect(jsonPath("$.message", containsString("无课程访问权限")));
    }

    @Test
    void teacherCannotSubmitStudentLabEndpoint() throws Exception {
        long labId = createPublishedLab(506L, true, LocalDateTime.now().plusDays(3));

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(teacherHeaders("506", "506", "601"))
                        .param("code", "print('teacher submit')")
                        .param("language", "python"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"))
                .andExpect(jsonPath("$.message", containsString("学生")));
    }

    @Test
    void submissionRejectsUnsupportedSourceFileType() throws Exception {
        long labId = createPublishedLab(507L, false, LocalDateTime.now().plusDays(3));

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "not source code".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file(invalidFile)
                        .headers(studentHeaders("507"))
                        .param("language", "python"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-06"))
                .andExpect(jsonPath("$.message", containsString("文件")));
    }

    @Test
    void studentCanViewOwnSubmissionHistoryInDescendingOrder() throws Exception {
        long labId = createPublishedLab(508L, true, LocalDateTime.now().plusDays(3));
        createCodeSubmission(labId, 601L, "508", "print('first')", "python");
        long latestSubmissionId = createCodeSubmission(labId, 601L, "508", "print('second')", "python");
        jdbcTemplate.update(
                "UPDATE lab_submission SET auto_score = ?, final_score = ? WHERE id = ?",
                95,
                98,
                latestSubmissionId
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("508", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].submissionId").value(latestSubmissionId))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].isLatest").value(true))
                .andExpect(jsonPath("$.data[0].isFinal").value(true))
                .andExpect(jsonPath("$.data[0].isScoringBasis").value(true))
                .andExpect(jsonPath("$.data[0].autoScore").value(95))
                .andExpect(jsonPath("$.data[0].finalScore").value(98))
                .andExpect(jsonPath("$.data[0].hasFile").value(false))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].isLatest").value(false))
                .andExpect(jsonPath("$.data[1].isFinal").value(false))
                .andExpect(jsonPath("$.data[1].isScoringBasis").value(false));
    }

    @Test
    void teacherCanFilterLabSubmissionHistoryAndViewSubmissionDetail() throws Exception {
        long labId = createPublishedLab(509L, true, LocalDateTime.now().plusDays(3));
        createCodeSubmission(labId, 601L, "509", "print('student 601')", "python");
        long targetSubmissionId = createCodeSubmission(labId, 602L, "509", "print('student 602 latest')", "python");

        jdbcTemplate.update(
                "UPDATE lab_submission SET submit_status = ?, evaluation_status = ?, auto_score = ?, final_score = ?, submitted_at = ? WHERE id = ?",
                "LATE",
                "ACCEPTED",
                88,
                90,
                java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(4)),
                targetSubmissionId
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .headers(teacherHeaders("509", "509"))
                        .param("studentId", "602")
                        .param("submitStatus", "LATE")
                        .param("evaluationStatus", "ACCEPTED")
                        .param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].submissionId").value(targetSubmissionId))
                .andExpect(jsonPath("$.data[0].studentId").value(602))
                .andExpect(jsonPath("$.data[0].submitStatus").value("LATE"))
                .andExpect(jsonPath("$.data[0].evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data[0].isLatest").value(true))
                .andExpect(jsonPath("$.data[0].isFinal").value(true))
                .andExpect(jsonPath("$.data[0].isScoringBasis").value(true));

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, targetSubmissionId)
                        .headers(teacherHeaders("509", "509")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").value(targetSubmissionId))
                .andExpect(jsonPath("$.data.studentId").value(602))
                .andExpect(jsonPath("$.data.code").value("print('student 602 latest')"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.hasFile").value(false))
                .andExpect(jsonPath("$.data.isScoringBasis").value(true));
    }

    @Test
    void teacherFiltersDoNotPromoteHistoricalSubmissionToLatest() throws Exception {
        long labId = createPublishedLab(512L, true, LocalDateTime.now().plusDays(3));
        long historicalSubmissionId = createCodeSubmission(labId, 602L, "512", "print('student 602 old')", "python");
        createCodeSubmission(labId, 602L, "512", "print('student 602 latest')", "python");

        jdbcTemplate.update(
                "UPDATE lab_submission SET submit_status = ?, evaluation_status = ? WHERE id = ?",
                "LATE",
                "ACCEPTED",
                historicalSubmissionId
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .headers(teacherHeaders("512", "512"))
                        .param("studentId", "602")
                        .param("submitStatus", "LATE")
                        .param("evaluationStatus", "ACCEPTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].submissionId").value(historicalSubmissionId))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].isLatest").value(false))
                .andExpect(jsonPath("$.data[0].isFinal").value(false))
                .andExpect(jsonPath("$.data[0].isScoringBasis").value(false));
    }

    @Test
    void studentCannotViewAnotherStudentsSubmissionDetail() throws Exception {
        long labId = createPublishedLab(510L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "510", "print('owner only')", "python");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(studentHeaders("510", 602L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));
    }

    @Test
    void submissionDetailReturnsNotFoundWhenSubmissionDoesNotBelongToLab() throws Exception {
        long sourceLabId = createPublishedLab(511L, true, LocalDateTime.now().plusDays(3));
        long anotherLabId = createPublishedLab(511L, true, LocalDateTime.now().plusDays(4));
        long submissionId = createCodeSubmission(sourceLabId, 601L, "511", "print('wrong lab path')", "python");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", anotherLabId, submissionId)
                        .headers(teacherHeaders("511", "511")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB-404-01"));
    }

    private long createPublishedLab(long courseId, boolean autoEvaluate, LocalDateTime deadline) throws Exception {
        Map<String, Object> payload = Map.ofEntries(
                entry("title", "学生提交实验"),
                entry("description", "用于学生提交流程验证"),
                entry("deadline", DEADLINE_FORMATTER.format(deadline)),
                entry("maxScore", 100),
                entry("attachmentIds", List.of(11, 12)),
                entry("allowedLanguages", "java,python"),
                entry("evaluationMode", "DOCKER_IO"),
                entry("autoEvaluate", autoEvaluate),
                entry("reportRequired", false),
                entry("timeLimitMs", 60000),
                entry("memoryLimitKb", 262144),
                entry("testcases", List.of())
        );

        String body = mockMvc.perform(post("/api/v1/courses/{courseId}/labs", courseId)
                        .headers(teacherHeaders(Long.toString(courseId), Long.toString(courseId)))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long labId = objectMapper.readTree(body).path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/labs/{labId}/publish", labId)
                        .headers(teacherHeaders(Long.toString(courseId), Long.toString(courseId), "601")))
                .andExpect(status().isOk());

        return labId;
    }

    private void deleteIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            // The red phase may run before the new table exists, so cleanup must be tolerant.
        }
    }

    private long createCodeSubmission(long labId, long studentId, String courseIds, String code, String language) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders(courseIds, studentId))
                        .param("code", code)
                        .param("language", language))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private HttpHeaders teacherHeaders(String courseIds, String manageableCourseIds) {
        return teacherHeaders(courseIds, manageableCourseIds, null);
    }

    private HttpHeaders teacherHeaders(String courseIds, String manageableCourseIds, String studentIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", courseIds);
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        if (studentIds != null) {
            headers.add("X-Course-Student-Ids", studentIds);
        }
        return headers;
    }

    private HttpHeaders studentHeaders(String courseIds) {
        return studentHeaders(courseIds, 601L);
    }

    private HttpHeaders studentHeaders(String courseIds, long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", Long.toString(userId));
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }
}
