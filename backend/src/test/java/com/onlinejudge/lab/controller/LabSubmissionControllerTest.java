package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_submission_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.evaluation.sandbox.mode=fake",
        "onlinejudge.evaluation.fake.delay-ms=150"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabSubmissionControllerTest {
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceGradeClient sourceGradeClient;

    @Autowired
    private FileStorageService fileStorageService;

    @BeforeEach
    void cleanTables() {
        deleteIfExists("DELETE FROM lab_evaluation_result");
        deleteIfExists("DELETE FROM lab_evaluation");
        deleteIfExists("DELETE FROM lab_score_change_log");
        deleteIfExists("DELETE FROM lab_score");
        deleteIfExists("DELETE FROM lab_report");
        deleteIfExists("DELETE FROM lab_submission_source_file");
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
                "text/x-python-script",
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
    void teacherCanInspectTrustedSourceMetadataAndDownloadTheExactSubmissionVersion() throws Exception {
        long labId = createPublishedLab(540L, false, LocalDateTime.now().plusDays(3));
        byte[] sourceBytes = "print('林晓的版本 3')\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long submissionId = createSourceSubmission(
                labId,
                "540",
                "实验源码-林晓.py",
                "text/x-python",
                sourceBytes
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(teacherHeaders("540", "540", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasFile").value(true))
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andExpect(jsonPath("$.data.sourceFile.originalFilename").value("实验源码-林晓.py"))
                .andExpect(jsonPath("$.data.sourceFile.contentType").value("text/x-python"))
                .andExpect(jsonPath("$.data.sourceFile.fileSize").value(sourceBytes.length))
                .andExpect(jsonPath("$.data.sourceFile.downloadAvailable").value(true))
                .andExpect(jsonPath("$.data.sourceFile.storageKey").doesNotExist());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(studentHeaders("540", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceFile.originalFilename").value("实验源码-林晓.py"))
                .andExpect(jsonPath("$.data.sourceFile.downloadAvailable").value(false))
                .andExpect(jsonPath("$.data.fileId").doesNotExist());

        Map<String, Object> metadata = jdbcTemplate.queryForMap(
                """
                SELECT submission_id, lab_id, course_id, uploader_id, storage_key,
                       original_filename, content_type, file_size, status
                  FROM lab_submission_source_file
                 WHERE submission_id = ?
                """,
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(metadata.get("SUBMISSION_ID")).isEqualTo(submissionId);
        org.assertj.core.api.Assertions.assertThat(metadata.get("LAB_ID")).isEqualTo(labId);
        org.assertj.core.api.Assertions.assertThat(metadata.get("COURSE_ID")).isEqualTo(540L);
        org.assertj.core.api.Assertions.assertThat(metadata.get("UPLOADER_ID")).isEqualTo(601L);
        org.assertj.core.api.Assertions.assertThat(metadata.get("STORAGE_KEY")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(metadata.get("ORIGINAL_FILENAME")).isEqualTo("实验源码-林晓.py");
        org.assertj.core.api.Assertions.assertThat(metadata.get("CONTENT_TYPE")).isEqualTo("text/x-python");
        org.assertj.core.api.Assertions.assertThat(metadata.get("FILE_SIZE")).isEqualTo((long) sourceBytes.length);
        org.assertj.core.api.Assertions.assertThat(metadata.get("STATUS")).isEqualTo("AVAILABLE");

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            submissionId
                        )
                        .headers(teacherHeaders("540", "540", "601")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/x-python"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, sourceBytes.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("%E5%AE%9E%E9%AA%8C")))
                .andExpect(content().bytes(sourceBytes));
    }

    @Test
    void sourceDownloadReauthorizesRoleAndCourseManagementWithoutLeakingStorageState() throws Exception {
        long labId = createPublishedLab(541L, false, LocalDateTime.now().plusDays(3));
        long submissionId = createSourceSubmission(
                labId,
                "541",
                "solution.py",
                "text/x-python",
                "print('secure')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            submissionId
                        ))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            submissionId
                        )
                        .headers(studentHeaders("541", 601L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR-AUTH-05"));

        String otherTeacherBody = mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            submissionId
                        )
                        .headers(teacherHeaders("999", "999", "601")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(otherTeacherBody)
                .doesNotContain("storage", "solution.py", "lab_submission_source_file");
    }

    @Test
    void sourceDownloadRejectsCrossLabAndDeletedSubmissionBindingsAsTheSameMissingTarget() throws Exception {
        long sourceLabId = createPublishedLab(542L, false, LocalDateTime.now().plusDays(3));
        long otherLabId = createPublishedLab(542L, false, LocalDateTime.now().plusDays(3));
        long submissionId = createSourceSubmission(
                sourceLabId,
                "542",
                "bound.py",
                "text/x-python",
                "print('bound')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            otherLabId,
                            submissionId
                        )
                        .headers(teacherHeaders("542", "542", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB-404-01"));

        jdbcTemplate.update("UPDATE lab_submission SET deleted = TRUE WHERE id = ?", submissionId);

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            sourceLabId,
                            submissionId
                        )
                        .headers(teacherHeaders("542", "542", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB-404-01"));
    }

    @Test
    void sourceDownloadDistinguishesNoFileLegacyMetadataAndDeletedAssets() throws Exception {
        long labId = createPublishedLab(543L, false, LocalDateTime.now().plusDays(3));
        long codeOnlySubmissionId = createCodeSubmission(labId, 601L, "543", "print('inline')", "python");

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            codeOnlySubmissionId
                        )
                        .headers(teacherHeaders("543", "543", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB-404-03"));

        long legacySubmissionId = createSourceSubmission(
                labId,
                "543",
                "legacy.py",
                "text/x-python",
                "print('legacy')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        jdbcTemplate.update(
                "DELETE FROM lab_submission_source_file WHERE submission_id = ?",
                legacySubmissionId
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, legacySubmissionId)
                        .headers(teacherHeaders("543", "543", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasFile").value(true))
                .andExpect(jsonPath("$.data.sourceFile").doesNotExist())
                .andExpect(jsonPath("$.data.fileId").doesNotExist());
        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            legacySubmissionId
                        )
                        .headers(teacherHeaders("543", "543", "601")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAB-409-03"));

        long deletedAssetSubmissionId = createSourceSubmission(
                labId,
                "543",
                "deleted.py",
                "text/x-python",
                "print('deleted')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        jdbcTemplate.update(
                "UPDATE lab_submission_source_file SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP WHERE submission_id = ?",
                deletedAssetSubmissionId
        );

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            deletedAssetSubmissionId
                        )
                        .headers(teacherHeaders("543", "543", "601")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAB-409-03"));
    }

    @Test
    void sourceDownloadReturnsStableStorageAndMetadataIntegrityErrors() throws Exception {
        long labId = createPublishedLab(544L, false, LocalDateTime.now().plusDays(3));
        long missingPhysicalSubmissionId = createSourceSubmission(
                labId,
                "544",
                "missing.py",
                "text/x-python",
                "print('missing')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        String missingStorageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM lab_submission_source_file WHERE submission_id = ?",
                String.class,
                missingPhysicalSubmissionId
        );
        fileStorageService.delete(missingStorageKey);

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            missingPhysicalSubmissionId
                        )
                        .headers(teacherHeaders("544", "544", "601")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LAB-500-05"));

        long invalidMimeSubmissionId = createSourceSubmission(
                labId,
                "544",
                "mime.py",
                "text/x-python",
                "print('mime')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        jdbcTemplate.update(
                "UPDATE lab_submission_source_file SET content_type = ? WHERE submission_id = ?",
                "invalid mime\r\nX-Injected: true",
                invalidMimeSubmissionId
        );

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            invalidMimeSubmissionId
                        )
                        .headers(teacherHeaders("544", "544", "601")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAB-409-03"));

        long traversalSubmissionId = createSourceSubmission(
                labId,
                "544",
                "traversal.py",
                "text/x-python",
                "print('traversal')".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        jdbcTemplate.update("UPDATE lab_submission SET file_id = ? WHERE id = ?", "../../outside.py", traversalSubmissionId);
        jdbcTemplate.update(
                "UPDATE lab_submission_source_file SET storage_key = ? WHERE submission_id = ?",
                "../../outside.py",
                traversalSubmissionId
        );

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            traversalSubmissionId
                        )
                        .headers(teacherHeaders("544", "544", "601")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LAB-500-05"));
    }

    @Test
    void sourceFilenameSanitizationPreventsPathAndHeaderInjectionWhileKeepingUnicode() throws Exception {
        long labId = createPublishedLab(545L, false, LocalDateTime.now().plusDays(3));
        byte[] sourceBytes = "print('safe')".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long submissionId = createSourceSubmission(
                labId,
                "545",
                "../..\\恶意\r\nInjected.py",
                "text/x-python",
                sourceBytes
        );

        String detailBody = mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}",
                            labId,
                            submissionId
                        )
                        .headers(teacherHeaders("545", "545", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String safeFilename = objectMapper.readTree(detailBody)
                .path("data")
                .path("sourceFile")
                .path("originalFilename")
                .asText();
        org.assertj.core.api.Assertions.assertThat(safeFilename)
                .isNotBlank()
                .doesNotContain("/", "\\", "\r", "\n")
                .endsWith(".py")
                .contains("恶意");

        mockMvc.perform(get(
                            "/api/v1/labs/{labId}/submissions/{submissionId}/source/download",
                            labId,
                            submissionId
                        )
                        .headers(teacherHeaders("545", "545", "601")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(sourceBytes));
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
                .andExpect(jsonPath("$.data[0].finalScore").doesNotExist())
                .andExpect(jsonPath("$.data[0].hasFile").value(false))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].isLatest").value(false))
                .andExpect(jsonPath("$.data[1].isFinal").value(false))
                .andExpect(jsonPath("$.data[1].isScoringBasis").value(false));

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .headers(teacherHeaders("508", "508", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].finalScore").value(98));

        mockMvc.perform(post("/api/v1/labs/{labId}/close", labId)
                        .headers(teacherHeaders("508", "508", "601")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .headers(teacherHeaders("508", "508", "601")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("508", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].finalScore").value(98));
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
    void autoEvaluateSubmissionEventuallyReturnsAcceptedAndHidesHiddenCaseFromStudent() throws Exception {
        long labId = createPublishedLab(
                513L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "1 2",
                        "expectedOutput", "sum:3",
                        "scoreWeight", 40,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ), Map.of(
                        "input", "2 3",
                        "expectedOutput", "sum:5",
                        "scoreWeight", 60,
                        "public", false,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 2
                ))
        );

        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("513"))
                        .param("code", """
                                first, second = map(int, input().split())
                                print(f"sum:{first + second}")
                                """)
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.autoScore").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long submissionId = objectMapper.readTree(body).path("data").path("submissionId").asLong();

        assertEvaluationStatusOneOf(labId, submissionId, studentHeaders("513"), Set.of("PENDING", "RUNNING"));
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("513", "513"), "ACCEPTED");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(studentHeaders("513")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passedCases").value(2))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.caseResults", hasSize(1)))
                .andExpect(jsonPath("$.data.caseResults[0].input").value("1 2"))
                .andExpect(jsonPath("$.data.caseResults[0].expectedOutput").value("sum:3"))
                .andExpect(jsonPath("$.data.caseResults[1]").doesNotExist());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("513", "513")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passedCases").value(2))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.caseResults", hasSize(2)))
                .andExpect(jsonPath("$.data.caseResults[1].input").value("2 3"))
                .andExpect(jsonPath("$.data.caseResults[1].expectedOutput").value("sum:5"));

        Integer aggregateRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_evaluation WHERE submission_id = ?",
                Integer.class,
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(aggregateRows).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM lab_evaluation WHERE submission_id = ?", submissionId);
        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(studentHeaders("513")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(100))
                .andExpect(jsonPath("$.data.passedCases").value(2))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.caseResults", hasSize(1)));
    }

    @Test
    void autoEvaluateSubmissionReturnsWrongAnswerAndPersistsCaseDetails() throws Exception {
        long labId = createPublishedLab(
                514L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(
                        Map.of(
                                "input", "case-a",
                                "expectedOutput", "answer-a",
                                "scoreWeight", 50,
                                "public", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 1
                        ),
                        Map.of(
                                "input", "case-b",
                                "expectedOutput", "answer-b",
                                "scoreWeight", 50,
                                "public", true,
                                "timeLimitMs", 1000,
                                "memoryLimitKb", 65536,
                                "orderNum", 2
                        )
                )
        );

        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("514"))
                        .param("code", """
                                value = input().strip()
                                if value == "case-a":
                                    print("answer-a")
                                else:
                                    print("wrong-b")
                                """)
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long submissionId = objectMapper.readTree(body).path("data").path("submissionId").asLong();
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("514", "514"), "WRONG_ANSWER");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("514", "514")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.score").value(50))
                .andExpect(jsonPath("$.data.passedCases").value(1))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.message", containsString("部分用例未通过")))
                .andExpect(jsonPath("$.data.caseResults[1].passed").value(false))
                .andExpect(jsonPath("$.data.caseResults[1].message", containsString("期望输出")))
                .andExpect(jsonPath("$.data.caseResults[1].actualOutput").value("wrong-b"));

        Integer evaluationRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_evaluation_result WHERE submission_id = ?",
                Integer.class,
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(evaluationRows).isEqualTo(2);

        Integer aggregateRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_evaluation WHERE submission_id = ?",
                Integer.class,
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(aggregateRows).isEqualTo(1);
    }

    @Test
    void studentCannotViewAnotherStudentsEvaluationResult() throws Exception {
        long labId = createPublishedLab(
                515L,
                false,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "1",
                        "expectedOutput", "1",
                        "scoreWeight", 100,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ))
        );
        long submissionId = createCodeSubmission(
                labId,
                601L,
                "515",
                """
                print(input().strip())
                """,
                "python"
        );

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(studentHeaders("515", 602L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));
    }

    @Test
    void teacherCanTriggerEvaluationEndpointForExistingSubmission() throws Exception {
        long labId = createPublishedLab(
                516L,
                false,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "5 6",
                        "expectedOutput", "11",
                        "scoreWeight", 100,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ))
        );
        long submissionId = createCodeSubmission(
                labId,
                601L,
                "516",
                """
                left, right = map(int, input().split())
                print(left + right)
                """,
                "python"
        );

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/evaluate", labId, submissionId)
                        .headers(teacherHeaders("516", "516")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.passedCases").value(0))
                .andExpect(jsonPath("$.data.totalCases").value(1));

        waitForEvaluationStatus(labId, submissionId, teacherHeaders("516", "516"), "ACCEPTED");
    }

    @Test
    void autoEvaluateSubmissionReturnsTimeLimitExceeded() throws Exception {
        long labId = createPublishedLab(
                517L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "ignored",
                        "expectedOutput", "ignored",
                        "scoreWeight", 100,
                        "public", true,
                        "timeLimitMs", 100,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ))
        );

        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("517"))
                        .param("code", "while True:\n    pass")
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(body).path("data").path("submissionId").asLong();
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("517", "517"), "TIME_LIMIT_EXCEEDED");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("517", "517")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.data.caseResults[0].message", containsString("超时")));
    }

    @Test
    void autoEvaluateSubmissionReturnsCompileError() throws Exception {
        long labId = createPublishedLab(
                518L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "ignored",
                        "expectedOutput", "ignored",
                        "scoreWeight", 100,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ))
        );

        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("518"))
                        .param("code", "print(")
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(body).path("data").path("submissionId").asLong();
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("518", "518"), "COMPILE_ERROR");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("518", "518")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.data.caseResults[0].message", containsString("编译失败")));
    }

    @Test
    void autoEvaluateSubmissionReturnsRuntimeError() throws Exception {
        long labId = createPublishedLab(
                519L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "ignored",
                        "expectedOutput", "ignored",
                        "scoreWeight", 100,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ))
        );

        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .headers(studentHeaders("519"))
                        .param("code", "raise RuntimeError('boom')")
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(body).path("data").path("submissionId").asLong();
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("519", "519"), "RUNTIME_ERROR");

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("519", "519")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.data.caseResults[0].message", containsString("运行时异常")));
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

    @Test
    void studentCanUploadReportTwiceAndTeacherCanViewLatestReportFromSubmissionDetail() throws Exception {
        long labId = createPublishedLab(520L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "520", "print('report linked')", "python");

        MockMultipartFile firstReport = new MockMultipartFile(
                "reportFile",
                "report-v1.pdf",
                "application/pdf",
                "report content v1".getBytes()
        );
        MockMultipartFile secondReport = new MockMultipartFile(
                "reportFile",
                "report-v2.pdf",
                "application/pdf",
                "report content v2".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(firstReport)
                        .headers(studentHeaders("520"))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.fileName").value("report-v1.pdf"))
                .andExpect(jsonPath("$.data.fileType").value("PDF"))
                .andExpect(jsonPath("$.data.submissionId").value(submissionId));

        String secondBody = mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(secondReport)
                        .headers(studentHeaders("520"))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = objectMapper.readTree(secondBody).path("data").path("reportId").asLong();

        mockMvc.perform(get("/api/v1/labs/{labId}/reports/{reportId}", labId, reportId)
                        .headers(teacherHeaders("520", "520", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.fileName").value("report-v2.pdf"))
                .andExpect(jsonPath("$.data.fileType").value("PDF"))
                .andExpect(jsonPath("$.data.downloadUrl", notNullValue()));

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(teacherHeaders("520", "520", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestReport.reportId").value(reportId))
                .andExpect(jsonPath("$.data.latestReport.version").value(2))
                .andExpect(jsonPath("$.data.latestReport.fileName").value("report-v2.pdf"))
                .andExpect(jsonPath("$.data.latestReport.fileType").value("PDF"));

        Integer versions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_report WHERE lab_id = ? AND student_id = ?",
                Integer.class,
                labId,
                601L
        );
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM lab_report WHERE lab_id = ? AND student_id = ?",
                Integer.class,
                labId,
                601L
        );

        org.assertj.core.api.Assertions.assertThat(versions).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(maxVersion).isEqualTo(2);
    }

    @Test
    void reportUploadRejectsUnsupportedFileTypeAndStudentCannotViewOthersReport() throws Exception {
        long labId = createPublishedLab(521L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "521", "print('report owner')", "python");

        MockMultipartFile invalidReport = new MockMultipartFile(
                "reportFile",
                "notes.txt",
                "text/plain",
                "invalid report".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(invalidReport)
                        .headers(studentHeaders("521"))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-06"))
                .andExpect(jsonPath("$.message", containsString("报告")));

        MockMultipartFile validReport = new MockMultipartFile(
                "reportFile",
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "valid report".getBytes()
        );
        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(validReport)
                        .headers(studentHeaders("521"))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long reportId = objectMapper.readTree(body).path("data").path("reportId").asLong();

        mockMvc.perform(get("/api/v1/labs/{labId}/reports/{reportId}", labId, reportId)
                        .headers(studentHeaders("521", 602L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));
    }

    @Test
    void expiredLabRejectsReportUpload() throws Exception {
        long labId = createPublishedLab(522L, true, LocalDateTime.now().plusDays(1));
        long submissionId = createCodeSubmission(labId, 601L, "522", "print('before deadline')", "python");
        jdbcTemplate.update("UPDATE lab_experiment SET deadline = ? WHERE id = ?", java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), labId);

        MockMultipartFile report = new MockMultipartFile(
                "reportFile",
                "late-report.pdf",
                "application/pdf",
                "late report".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(report)
                        .headers(studentHeaders("522"))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAB-409-01"))
                .andExpect(jsonPath("$.message", containsString("实验已截止")));
    }

    @Test
    void teacherCanScoreUploadedReport() throws Exception {
        long labId = createPublishedLab(523L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "523", "print('score report')", "python");
        long reportId = uploadReport(labId, submissionId, "523");

        mockMvc.perform(put("/api/v1/labs/{labId}/reports/{reportId}/score", labId, reportId)
                        .headers(teacherHeaders("523", "523", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", 95,
                                "comment", "报告完整"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.score").value(95))
                .andExpect(jsonPath("$.data.comment").value("报告完整"));

        Map<String, Object> scoredRecord = jdbcTemplate.queryForMap(
                "SELECT score, comment, scored_by, scored_at FROM lab_report WHERE id = ?",
                reportId
        );
        org.assertj.core.api.Assertions.assertThat(scoredRecord.get("SCORE")).isEqualTo(95);
        org.assertj.core.api.Assertions.assertThat(scoredRecord.get("COMMENT")).isEqualTo("报告完整");
        org.assertj.core.api.Assertions.assertThat(scoredRecord.get("SCORED_BY")).isEqualTo(501L);
        org.assertj.core.api.Assertions.assertThat(scoredRecord.get("SCORED_AT")).isNotNull();

        mockMvc.perform(get("/api/v1/labs/{labId}/reports/{reportId}", labId, reportId)
                        .headers(studentHeaders("523", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist());

        mockMvc.perform(get("/api/v1/labs/{labId}/reports/{reportId}", labId, reportId)
                        .headers(teacherHeaders("523", "523", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(95))
                .andExpect(jsonPath("$.data.comment").value("报告完整"));

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(teacherHeaders("523", "523", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestReport.reportId").value(reportId))
                .andExpect(jsonPath("$.data.latestReport.score").value(95))
                .andExpect(jsonPath("$.data.latestReport.comment").value("报告完整"));

        mockMvc.perform(post("/api/v1/labs/{labId}/close", labId)
                        .headers(teacherHeaders("523", "523", "601")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .headers(teacherHeaders("523", "523", "601")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}/reports/{reportId}", labId, reportId)
                        .headers(studentHeaders("523", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(95))
                .andExpect(jsonPath("$.data.comment").value("报告完整"));
    }

    @Test
    void reportScoreRejectsOutOfRangeAndNonManager() throws Exception {
        long labId = createPublishedLab(524L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "524", "print('score guard')", "python");
        long reportId = uploadReport(labId, submissionId, "524");

        mockMvc.perform(put("/api/v1/labs/{labId}/reports/{reportId}/score", labId, reportId)
                        .headers(teacherHeaders("524", "524", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", 101,
                                "comment", "超过满分"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-06"))
                .andExpect(jsonPath("$.message", containsString("评分")));

        mockMvc.perform(put("/api/v1/labs/{labId}/reports/{reportId}/score", labId, reportId)
                        .headers(teacherHeaders("524", "", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", 90,
                                "comment", "无管理权限"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));

        mockMvc.perform(put("/api/v1/labs/{labId}/reports/{reportId}/score", labId, reportId)
                        .headers(studentHeaders("524"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", 90,
                                "comment", "学生不能评分"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanScoreSubmissionAndPersistScoreRecord() throws Exception {
        long labId = createPublishedLab(525L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "525", "print('score submission')", "python");
        String internalFileId = "internal/lab/submissions/" + submissionId + "/main.py";
        jdbcTemplate.update("UPDATE lab_submission SET file_id = ? WHERE id = ?", internalFileId, submissionId);
        long reportId = uploadReport(labId, submissionId, "525");
        jdbcTemplate.update("UPDATE lab_report SET score = ?, comment = ?, scored_by = ?, scored_at = ? WHERE id = ?",
                30,
                "报告得分 30",
                501L,
                java.sql.Timestamp.valueOf(LocalDateTime.now()),
                reportId);
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("525", "525", "601"), "ACCEPTED");

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("525", "525", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 92,
                                "reportScore", 30,
                                "finalScore", 95,
                                "comment", "整体实现稳定"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.manualScore").value(92))
                .andExpect(jsonPath("$.data.reportScore").value(30))
                .andExpect(jsonPath("$.data.finalScore").value(95))
                .andExpect(jsonPath("$.data.comment").value("整体实现稳定"))
                .andExpect(jsonPath("$.data.hasChangeLogs").value(false));

        Map<String, Object> scoreRecord = jdbcTemplate.queryForMap(
                "SELECT auto_score, report_score, manual_score, final_score, comment, teacher_id FROM lab_score WHERE submission_id = ?",
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("AUTO_SCORE")).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("REPORT_SCORE")).isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("MANUAL_SCORE")).isEqualTo(92);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("FINAL_SCORE")).isEqualTo(95);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("COMMENT")).isEqualTo("整体实现稳定");
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("TEACHER_ID")).isEqualTo(501L);

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(teacherHeaders("525", "525", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestScore.manualScore").value(92))
                .andExpect(jsonPath("$.data.latestScore.reportScore").value(30))
                .andExpect(jsonPath("$.data.latestScore.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestScore.comment").value("整体实现稳定"))
                .andExpect(jsonPath("$.data.latestScore.hasChangeLogs").value(false))
                .andExpect(jsonPath("$.data.fileId").doesNotExist());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(studentHeaders("525", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("print('score submission')"))
                .andExpect(jsonPath("$.data.hasFile").value(true))
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andExpect(jsonPath("$.data.finalScore").doesNotExist())
                .andExpect(jsonPath("$.data.latestReport.score").doesNotExist())
                .andExpect(jsonPath("$.data.latestReport.comment").doesNotExist())
                .andExpect(jsonPath("$.data.latestScore").doesNotExist());

        mockMvc.perform(post("/api/v1/labs/{labId}/close", labId)
                        .headers(teacherHeaders("525", "525", "601")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .headers(teacherHeaders("525", "525", "601")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}", labId, submissionId)
                        .headers(studentHeaders("525", 601L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andExpect(jsonPath("$.data.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestReport.score").value(30))
                .andExpect(jsonPath("$.data.latestReport.comment").value("报告得分 30"))
                .andExpect(jsonPath("$.data.latestScore.manualScore").value(92))
                .andExpect(jsonPath("$.data.latestScore.reportScore").value(30))
                .andExpect(jsonPath("$.data.latestScore.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestScore.comment").value("整体实现稳定"));
    }

    @Test
    void updatingSubmissionScoreRequiresReasonAndPersistsChangeLog() throws Exception {
        long labId = createPublishedLab(526L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "526", "print('change score')", "python");
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("526", "526", "601"), "ACCEPTED");

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("526", "526", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 88,
                                "finalScore", 88,
                                "comment", "首次评分"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(88));

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("526", "526", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 90,
                                "finalScore", 90,
                                "comment", "缺少修改原因"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-05"))
                .andExpect(jsonPath("$.message", containsString("修改原因")));

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("526", "526", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 90,
                                "finalScore", 90,
                                "comment", "修正边界分",
                                "changeReason", "核对评分标准后修正"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(90))
                .andExpect(jsonPath("$.data.hasChangeLogs").value(true));

        Map<String, Object> scoreRecord = jdbcTemplate.queryForMap(
                "SELECT final_score, manual_score, comment FROM lab_score WHERE submission_id = ?",
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("FINAL_SCORE")).isEqualTo(90);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("MANUAL_SCORE")).isEqualTo(90);
        org.assertj.core.api.Assertions.assertThat(scoreRecord.get("COMMENT")).isEqualTo("修正边界分");

        Map<String, Object> changeLog = jdbcTemplate.queryForMap(
                "SELECT old_final_score, new_final_score, reason, operator_id FROM lab_score_change_log WHERE score_id = (SELECT id FROM lab_score WHERE submission_id = ?)",
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(changeLog.get("OLD_FINAL_SCORE")).isEqualTo(88);
        org.assertj.core.api.Assertions.assertThat(changeLog.get("NEW_FINAL_SCORE")).isEqualTo(90);
        org.assertj.core.api.Assertions.assertThat(changeLog.get("REASON")).isEqualTo("核对评分标准后修正");
        org.assertj.core.api.Assertions.assertThat(changeLog.get("OPERATOR_ID")).isEqualTo(501L);
    }

    @Test
    void submissionScoreRejectsOutOfRangeAndInvalidAccess() throws Exception {
        long labId = createPublishedLab(527L, true, LocalDateTime.now().plusDays(3));
        long submissionId = createCodeSubmission(labId, 601L, "527", "print('invalid score')", "python");

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("527", "527", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 101,
                                "finalScore", 101,
                                "comment", "越界"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LAB-400-05"));

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("527", "", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 80,
                                "finalScore", 80,
                                "comment", "无权限"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(studentHeaders("527"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 80,
                                "finalScore", 80,
                                "comment", "学生不能评分"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId + 999)
                        .headers(teacherHeaders("527", "527", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 80,
                                "finalScore", 80,
                                "comment", "不存在"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAB-404-01"));
    }

    @Test
    void studentResultViewHidesTeacherScoreUntilLabScoresAreReleased() throws Exception {
        long labId = createPublishedLab(
                528L,
                true,
                LocalDateTime.now().plusDays(3),
                List.of(Map.of(
                        "input", "1 2",
                        "expectedOutput", "sum:3",
                        "scoreWeight", 40,
                        "public", true,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 1
                ), Map.of(
                        "input", "2 3",
                        "expectedOutput", "sum:5",
                        "scoreWeight", 60,
                        "public", false,
                        "timeLimitMs", 1000,
                        "memoryLimitKb", 65536,
                        "orderNum", 2
                ))
        );
        long submissionId = createCodeSubmission(labId, 601L, "528", """
                first, second = map(int, input().split())
                print(f"sum:{first + second}")
                """, "python");
        waitForEvaluationStatus(labId, submissionId, teacherHeaders("528", "528", "601"), "ACCEPTED");
        long reportId = uploadReport(labId, submissionId, "528");

        mockMvc.perform(put("/api/v1/labs/{labId}/reports/{reportId}/score", labId, reportId)
                        .headers(teacherHeaders("528", "528", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "score", 30,
                                "comment", "报告结构完整"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, submissionId)
                        .headers(teacherHeaders("528", "528", "601"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 92,
                                "reportScore", 30,
                                "finalScore", 95,
                                "comment", "整体实现稳定"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, 601L)
                        .headers(studentHeaders("528")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.studentId").value(601))
                .andExpect(jsonPath("$.data.labId").value(labId))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.submission.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.submission.finalScore").doesNotExist())
                .andExpect(jsonPath("$.data.latestScore").doesNotExist())
                .andExpect(jsonPath("$.data.latestReport.score").doesNotExist())
                .andExpect(jsonPath("$.data.latestReport.comment").doesNotExist())
                .andExpect(jsonPath("$.data.evaluationResult.score").value(100))
                .andExpect(jsonPath("$.data.evaluationResult.passedCases").value(2))
                .andExpect(jsonPath("$.data.evaluationResult.totalCases").value(2))
                .andExpect(jsonPath("$.data.evaluationResult.caseResults", hasSize(1)))
                .andExpect(jsonPath("$.data.evaluationResult.caseResults[0].expectedOutput").value("sum:3"))
                .andExpect(jsonPath("$.data.evaluationResult.caseResults[1]").doesNotExist())
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist());

        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .headers(teacherHeaders("528", "528", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));

        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, 601L)
                        .headers(studentHeaders("528")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"))
                .andExpect(jsonPath("$.data.submission.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestScore.manualScore").value(92))
                .andExpect(jsonPath("$.data.latestScore.reportScore").value(30))
                .andExpect(jsonPath("$.data.latestScore.finalScore").value(95))
                .andExpect(jsonPath("$.data.latestScore.comment").value("整体实现稳定"))
                .andExpect(jsonPath("$.data.latestReport.score").value(30))
                .andExpect(jsonPath("$.data.latestReport.comment").value("报告结构完整"))
                .andExpect(jsonPath("$.data.publishedAt").exists());
    }

    @Test
    void releasedLabScoresExposeSourceGradesForGrdSync() throws Exception {
        long labId = createPublishedLab(530L, true, LocalDateTime.now().plusDays(3));
        long firstSubmissionId = createCodeSubmission(labId, 601L, "530", "print('student 601')", "python");
        long secondSubmissionId = createCodeSubmission(labId, 602L, "530", "print('student 602')", "python");
        waitForEvaluationStatus(labId, firstSubmissionId, teacherHeaders("530", "530", "601,602"), "ACCEPTED");
        waitForEvaluationStatus(labId, secondSubmissionId, teacherHeaders("530", "530", "601,602"), "ACCEPTED");

        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, firstSubmissionId)
                        .headers(teacherHeaders("530", "530", "601,602"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 92,
                                "finalScore", 95,
                                "comment", "整体实现稳定"
                        ))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/labs/{labId}/submissions/{submissionId}/score", labId, secondSubmissionId)
                        .headers(teacherHeaders("530", "530", "601,602"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 75,
                                "finalScore", 78,
                                "comment", "基础功能完成"
                        ))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(sourceGradeClient.findSourceGrades(530L, SourceGradeType.LAB, labId))
                .isEmpty();

        mockMvc.perform(put("/api/v1/labs/{labId}/release-scores", labId)
                        .headers(teacherHeaders("530", "530", "601,602")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCORE_PUBLISHED"));

        org.assertj.core.api.Assertions.assertThat(sourceGradeClient.findSourceGrades(530L, SourceGradeType.LAB, labId))
                .extracting("studentId", "score", "fullScore", "status")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(601L, BigDecimal.valueOf(95), BigDecimal.valueOf(100), "SCORED"),
                        org.assertj.core.groups.Tuple.tuple(602L, BigDecimal.valueOf(78), BigDecimal.valueOf(100), "SCORED")
                );
        org.assertj.core.api.Assertions.assertThat(sourceGradeClient.findSourceGrades(530L, SourceGradeType.LAB, labId + 999))
                .isEmpty();
    }

    @Test
    void studentCannotQueryAnotherStudentsLabResult() throws Exception {
        long labId = createPublishedLab(529L, true, LocalDateTime.now().plusDays(3));
        createCodeSubmission(labId, 601L, "529", "print('owner')", "python");

        mockMvc.perform(get("/api/v1/labs/{labId}/results/{studentId}", labId, 601L)
                        .headers(studentHeaders("529", 602L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LAB-403-01"));
    }

    private long createPublishedLab(long courseId, boolean autoEvaluate, LocalDateTime deadline) throws Exception {
        return createPublishedLab(courseId, autoEvaluate, deadline, List.of());
    }

    private long createPublishedLab(
            long courseId,
            boolean autoEvaluate,
            LocalDateTime deadline,
            List<Map<String, Object>> testcases
    ) throws Exception {
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
                entry("testcases", testcases)
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

    private long createSourceSubmission(
            long labId,
            String courseIds,
            String filename,
            String contentType,
            byte[] bytes
    ) throws Exception {
        MockMultipartFile sourceFile = new MockMultipartFile("file", filename, contentType, bytes);
        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/submissions", labId)
                        .file(sourceFile)
                        .headers(studentHeaders(courseIds))
                        .param("language", "python"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private long uploadReport(long labId, long submissionId, String courseIds) throws Exception {
        MockMultipartFile report = new MockMultipartFile(
                "reportFile",
                "report.pdf",
                "application/pdf",
                "report content".getBytes()
        );
        String body = mockMvc.perform(multipart("/api/v1/labs/{labId}/reports", labId)
                        .file(report)
                        .headers(studentHeaders(courseIds))
                        .param("submissionId", String.valueOf(submissionId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("reportId").asLong();
    }

    private void assertEvaluationStatusOneOf(long labId, long submissionId, HttpHeaders headers, Set<String> statuses) throws Exception {
        String body = mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(headers))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String actualStatus = objectMapper.readTree(body).path("data").path("evaluationStatus").asText();
        org.assertj.core.api.Assertions.assertThat(statuses).contains(actualStatus);
    }

    private void waitForEvaluationStatus(long labId, long submissionId, HttpHeaders headers, String expectedStatus) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        String actualStatus = "";
        while (System.nanoTime() < deadline) {
            String body = mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                            .headers(headers))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            actualStatus = objectMapper.readTree(body).path("data").path("evaluationStatus").asText();
            if (expectedStatus.equals(actualStatus)) {
                return;
            }
            Thread.sleep(50);
        }
        org.assertj.core.api.Assertions.assertThat(actualStatus).isEqualTo(expectedStatus);
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
