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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "601");
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }
}
