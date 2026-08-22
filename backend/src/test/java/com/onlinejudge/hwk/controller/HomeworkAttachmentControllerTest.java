package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.common.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_attachment_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.servlet.multipart.max-file-size=12MB",
        "spring.servlet.multipart.max-request-size=12MB",
        "onlinejudge.hwk.attachments.max-size-bytes=10485760",
        "onlinejudge.hwk.attachments.upload-ttl=PT24H",
        "onlinejudge.hwk.attachments.zip-max-entries=32",
        "onlinejudge.hwk.attachments.cleanup-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeworkAttachmentControllerTest {
    private static final byte[] VALID_PDF = (
            "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
    ).getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FileStorageService fileStorageService;

    private final RecordingFileStorageService storage = new RecordingFileStorageService();

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void cleanState() {
        reset(fileStorageService);
        when(fileStorageService.store(anyString(), any(), any(InputStream.class))).thenAnswer(invocation -> storage.store(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2)
        ));
        when(fileStorageService.load(anyString())).thenAnswer(invocation -> storage.load(invocation.getArgument(0)));
        doAnswer(invocation -> {
            storage.delete(invocation.getArgument(0));
            return null;
        }).when(fileStorageService).delete(anyString());
        doAnswer(invocation -> {
            storage.deferDelete(invocation.getArgument(0));
            return null;
        }).when(fileStorageService).deferDelete(anyString());
        when(fileStorageService.pendingDeletes(anyInt()))
                .thenAnswer(invocation -> storage.pendingDeletes(invocation.getArgument(0)));
        doAnswer(invocation -> {
            storage.completeDeferredDelete(invocation.getArgument(0));
            return null;
        }).when(fileStorageService).completeDeferredDelete(anyString());
        deleteIfExists("DELETE FROM t_hwk_submission_attachment");
        deleteIfExists("DELETE FROM t_hwk_submission");
        deleteIfExists("DELETE FROM t_hwk_test_case");
        deleteIfExists("DELETE FROM t_hwk_question");
        deleteIfExists("DELETE FROM t_hwk_judge_config");
        deleteIfExists("DELETE FROM t_hwk_homework");
        storage.reset();
    }

    @Test
    void studentUploadsRevalidatesAndRemovesOneUnboundAttachmentWithoutLeakingStorageIdentifiers() throws Exception {
        long homeworkId = createPublishedFileHomework(101L);

        String body = upload(homeworkId, studentHeaders("101", "601"), "作业答案.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.fileId").isString())
                .andExpect(jsonPath("$.data.originalFilename").value("作业答案.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSize").value(VALID_PDF.length))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.uploadedAt").exists())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.url").doesNotExist())
                .andExpect(jsonPath("$.data.path").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String fileId = objectMapper.readTree(body).path("data").path("fileId").asText();

        assertThat(UUID.fromString(fileId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                fileId
        )).isEqualTo("UPLOADED");

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, fileId)
                        .headers(studentHeaders("101", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId))
                .andExpect(jsonPath("$.data.originalFilename").value("作业答案.pdf"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.url").doesNotExist());

        mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, fileId)
                        .headers(studentHeaders("101", "601")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, fileId)
                        .headers(studentHeaders("101", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"));
    }

    @Test
    void uploadingAReplacementLeavesOnlyTheNewestActiveUpload() throws Exception {
        long homeworkId = createPublishedFileHomework(119L);
        HttpHeaders headers = studentHeaders("119", "601");
        String firstId = uploadAndReturnId(homeworkId, headers);
        String firstStorageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                firstId
        );

        String secondId = uploadAndReturnId(homeworkId, headers);

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM t_hwk_submission_attachment
                 WHERE homework_id = ? AND uploader_id = 601 AND status = 'UPLOADED'
                """,
                Integer.class,
                homeworkId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active_slot FROM t_hwk_submission_attachment WHERE public_id = ?",
                Integer.class,
                secondId
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission_attachment WHERE public_id = ?",
                Integer.class,
                firstId
        )).isZero();
        assertThat(storage.contains(firstStorageKey)).isFalse();

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, firstId)
                        .headers(headers))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"));
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, secondId)
                        .headers(headers))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentFirstUploadsKeepOneActiveMetadataRowAndOnePhysicalObject() throws Exception {
        long homeworkId = createPublishedFileHomework(120L);
        HttpHeaders headers = studentHeaders("120", "601");
        storage.awaitConcurrentStores(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<org.springframework.test.web.servlet.MvcResult> first = executor.submit(() ->
                    upload(homeworkId, headers, "first.pdf", "application/pdf", VALID_PDF)
                            .andReturn()
            );
            Future<org.springframework.test.web.servlet.MvcResult> second = executor.submit(() ->
                    upload(homeworkId, headers, "second.pdf", "application/pdf", VALID_PDF)
                            .andReturn()
            );

            java.util.List<org.springframework.test.web.servlet.MvcResult> results = java.util.List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(201, 409);
            org.springframework.test.web.servlet.MvcResult conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(objectMapper.readTree(conflict.getResponse().getContentAsString()).path("code").asText())
                    .isEqualTo("HWK_4092");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM t_hwk_submission_attachment
                 WHERE homework_id = ? AND uploader_id = 601 AND status = 'UPLOADED'
                """,
                Integer.class,
                homeworkId
        )).isOne();
        assertThat(storage.size()).isOne();
        assertThat(storage.pendingDeleteCount()).isZero();
    }

    @Test
    void uploadRejectsMultipleFilePartsInOneRequest() throws Exception {
        long homeworkId = createPublishedFileHomework(110L);

        mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                        .headers(studentHeaders("110", "601")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                        .file(new MockMultipartFile("file", "first.pdf", "application/pdf", VALID_PDF))
                        .file(new MockMultipartFile("file", "second.pdf", "application/pdf", VALID_PDF))
                        .headers(studentHeaders("110", "601")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                        .file(new MockMultipartFile("file", "answer.pdf", "application/pdf", VALID_PDF))
                        .file(new MockMultipartFile("unexpected", "second.pdf", "application/pdf", VALID_PDF))
                        .headers(studentHeaders("110", "601")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        assertThat(countAttachments(homeworkId)).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void uploadRejectsEmptyOversizedAndDisguisedFilesBeforeCreatingMetadata() throws Exception {
        long homeworkId = createPublishedFileHomework(102L);
        HttpHeaders headers = studentHeaders("102", "601");

        upload(homeworkId, headers, "empty.pdf", "application/pdf", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(VALID_PDF, 0, oversized, 0, VALID_PDF.length);
        upload(homeworkId, headers, "large.pdf", "application/pdf", oversized)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("HWK_4131"));

        upload(
                homeworkId,
                headers,
                "disguised.pdf",
                "application/pdf",
                "<script>alert('not a pdf')</script>".getBytes(StandardCharsets.UTF_8)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        upload(homeworkId, headers, "malware.exe", "application/octet-stream", new byte[]{'M', 'Z'})
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HWK_4151"));
        upload(homeworkId, headers, "answer.pdf", "text/plain", VALID_PDF)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HWK_4151"));
        upload(homeworkId, headers, "answer.pdf", "application/octet-stream", VALID_PDF)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HWK_4151"));
        upload(homeworkId, headers, "answer.pdf", null, VALID_PDF)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("HWK_4151"));

        assertThat(countAttachments(homeworkId)).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void zipAndOoxmlUploadsRequireParseableBoundedArchiveStructure() throws Exception {
        long homeworkId = createPublishedFileHomework(111L);
        HttpHeaders headers = studentHeaders("111", "601");

        upload(homeworkId, headers, "valid.zip", "application/zip", zip(Map.of("notes.txt", "ok")))
                .andExpect(status().isCreated());
        upload(homeworkId, headers, "fake.zip", "application/zip", new byte[]{'P', 'K', 0x03, 0x04, 0x00})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));
        upload(homeworkId, headers, "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zip(Map.of("notes.txt", "not OOXML")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        for (ArchiveCase archive : java.util.List.of(
                new ArchiveCase("answer.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "word/document.xml"),
                new ArchiveCase("answer.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xl/workbook.xml"),
                new ArchiveCase("answer.pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation", "ppt/presentation.xml")
        )) {
            upload(homeworkId, headers, archive.filename(), archive.contentType(), zip(Map.of(
                    "[Content_Types].xml", "<Types/>",
                    archive.requiredEntry(), "<document/>"
            ))).andExpect(status().isCreated());
        }

        java.util.LinkedHashMap<String, String> tooManyEntries = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 33; index++) {
            tooManyEntries.put("entry-" + index + ".txt", "x");
        }
        upload(homeworkId, headers, "too-many.zip", "application/zip", zip(tooManyEntries))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));
    }

    @Test
    void nonFileHomeworkRejectsAnyAttachmentIdentifiers() throws Exception {
        long homeworkId = createPublishedHomework(112L, "TEXT");

        submit(homeworkId, studentHeaders("112", "601"), Map.of(
                "answerText", "real text answer",
                "fileIds", java.util.List.of("opaque-but-invalid-here")
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));
        submit(homeworkId, studentHeaders("112", "601"), Map.of(
                "answerText", "real text answer",
                "fileIds", java.util.List.of()
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id = ?",
                Integer.class,
                homeworkId
        )).isZero();
    }

    @Test
    void uploadRequiresStudentCourseMembershipAndPublishedFileHomework() throws Exception {
        long homeworkId = createPublishedFileHomework(103L);

        upload(homeworkId, studentHeaders("999", "601"), "answer.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        upload(homeworkId, teacherHeaders("103", "103"), "answer.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        jdbcTemplate.update("UPDATE t_hwk_homework SET status = 'DRAFT' WHERE id = ?", homeworkId);
        upload(homeworkId, studentHeaders("103", "601"), "answer.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4002"));

        assertThat(countAttachments(homeworkId)).isZero();
    }

    @Test
    void unboundAttachmentLookupAndRemovalHideOtherStudentsAndOtherHomeworks() throws Exception {
        long homeworkId = createPublishedFileHomework(104L);
        long otherHomeworkId = createPublishedFileHomework(104L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("104", "601"));

        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, fileId)
                        .headers(studentHeaders("104", "602")),
                get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", otherHomeworkId, fileId)
                        .headers(studentHeaders("104", "601")),
                delete("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, fileId)
                        .headers(studentHeaders("104", "602"))
        }) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HWK_4042"))
                    .andExpect(jsonPath("$.message").doesNotExist());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                fileId
        )).isEqualTo("UPLOADED");
    }

    @Test
    void attachmentPublicIdentifiersMustBeCanonicalUuidsBeforeLookupOrBinding() throws Exception {
        long homeworkId = createPublishedFileHomework(115L);
        HttpHeaders headers = studentHeaders("115", "601");

        for (String invalidFileId : java.util.List.of("not-a-uuid", "x".repeat(4096))) {
            mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, invalidFileId)
                            .headers(headers))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HWK_4042"))
                    .andExpect(jsonPath("$.message").doesNotExist());
            mockMvc.perform(delete("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, invalidFileId)
                            .headers(headers))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HWK_4042"))
                    .andExpect(jsonPath("$.message").doesNotExist());
            submit(homeworkId, headers, Map.of("fileIds", java.util.List.of(invalidFileId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("HWK_4042"))
                    .andExpect(jsonPath("$.message").doesNotExist());
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id = ?",
                Integer.class,
                homeworkId
        )).isZero();
    }

    @Test
    void unboundLookupReportsRetryableStorageFailureAndRejectsMalformedMetadataAsUnavailable() throws Exception {
        long homeworkId = createPublishedFileHomework(113L);
        String missingId = uploadAndReturnId(homeworkId, studentHeaders("113", "601"));
        String missingStorageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                missingId
        );
        storage.delete(missingStorageKey);

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, missingId)
                        .headers(studentHeaders("113", "601")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("HWK_5002"))
                .andExpect(jsonPath("$.data").doesNotExist());

        String malformedId = uploadAndReturnId(homeworkId, studentHeaders("113", "601"));
        jdbcTemplate.update(
                "UPDATE t_hwk_submission_attachment SET original_filename = '' WHERE public_id = ?",
                malformedId
        );
        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/attachments/{fileId}", homeworkId, malformedId)
                        .headers(studentHeaders("113", "601")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4091"));
    }

    @Test
    void fileSubmissionRequiresExactlyOneCurrentOwnedUploadAndBindsItOnce() throws Exception {
        long homeworkId = createPublishedFileHomework(105L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("105", "601"));

        submit(homeworkId, studentHeaders("105", "601"), Map.of("fileIds", java.util.List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));
        submit(homeworkId, studentHeaders("105", "601"), Map.of("fileIds", java.util.List.of(fileId, "second")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HWK_4005"));

        String submissionBody = submit(
                homeworkId,
                studentHeaders("105", "601"),
                Map.of("fileIds", java.util.List.of(fileId))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.submissionId").isNumber())
                .andExpect(jsonPath("$.data.fileIds").doesNotExist())
                .andExpect(jsonPath("$.data.fileUrl").doesNotExist())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(submissionBody).path("data").path("submissionId").asLong();

        assertThat(jdbcTemplate.queryForMap(
                "SELECT submission_id, status, expires_at, bound_at FROM t_hwk_submission_attachment WHERE public_id = ?",
                fileId
        )).containsEntry("SUBMISSION_ID", submissionId)
                .containsEntry("STATUS", "BOUND");

        submit(homeworkId, studentHeaders("105", "601"), Map.of("fileIds", java.util.List.of(fileId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4092"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id = ? AND student_id = 601",
                Integer.class,
                homeworkId
        )).isEqualTo(1);
    }

    @Test
    void fileSubmissionRejectsCrossStudentCrossHomeworkAndExpiredUploadsWithoutPartialSubmission() throws Exception {
        long homeworkId = createPublishedFileHomework(106L);
        long otherHomeworkId = createPublishedFileHomework(106L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("106", "601"));

        submit(homeworkId, studentHeaders("106", "602"), Map.of("fileIds", java.util.List.of(fileId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"));
        submit(otherHomeworkId, studentHeaders("106", "601"), Map.of("fileIds", java.util.List.of(fileId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"));

        jdbcTemplate.update(
                "UPDATE t_hwk_submission_attachment SET expires_at = ? WHERE public_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                fileId
        );
        submit(homeworkId, studentHeaders("106", "601"), Map.of("fileIds", java.util.List.of(fileId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HWK_4091"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission WHERE homework_id IN (?, ?)",
                Integer.class,
                homeworkId,
                otherHomeworkId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                fileId
        )).isEqualTo("UPLOADED");
    }

    @Test
    void attachmentDownloadReturnsExactBoundVersionOnlyToOwnerOrCourseManager() throws Exception {
        long homeworkId = createPublishedFileHomework(107L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("107", "601"));
        long submissionId = submitAndReturnId(homeworkId, fileId, studentHeaders("107", "601"));

        for (HttpHeaders allowed : java.util.List.of(
                studentHeaders("107", "601"),
                teacherHeaders("107", "107")
        )) {
            mockMvc.perform(get(
                            "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                            homeworkId,
                            submissionId
                    ).headers(allowed))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, VALID_PDF.length))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andExpect(content().bytes(VALID_PDF));
        }

        mockMvc.perform(get(
                        "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                        homeworkId,
                        submissionId
                ).headers(studentHeaders("107", "602")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        mockMvc.perform(get(
                        "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                        homeworkId,
                        submissionId
                ).headers(teacherHeaders("107", "999")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));
    }

    @Test
    void downloadHidesMissingAndDeletedHomeworkAsAttachmentNotFound() throws Exception {
        long homeworkId = createPublishedFileHomework(116L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("116", "601"));
        long submissionId = submitAndReturnId(homeworkId, fileId, studentHeaders("116", "601"));

        mockMvc.perform(get(
                        "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                        999999L,
                        submissionId
                ).headers(studentHeaders("116", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"))
                .andExpect(jsonPath("$.message").doesNotExist());

        jdbcTemplate.update("UPDATE t_hwk_homework SET is_deleted = TRUE WHERE id = ?", homeworkId);
        mockMvc.perform(get(
                        "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                        homeworkId,
                        submissionId
                ).headers(studentHeaders("116", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void downloadChecksCourseMembershipBeforeLookingUpSubmission() throws Exception {
        long homeworkId = createPublishedFileHomework(117L);
        String fileId = uploadAndReturnId(homeworkId, studentHeaders("117", "601"));
        long submissionId = submitAndReturnId(homeworkId, fileId, studentHeaders("117", "601"));
        HttpHeaders nonMember = studentHeaders("999", "602");

        for (long requestedSubmissionId : java.util.List.of(submissionId, Long.MAX_VALUE)) {
            mockMvc.perform(get(
                            "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                            homeworkId,
                            requestedSubmissionId
                    ).headers(nonMember))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("HWK_4031"));
        }
    }

    @Test
    void downloadHidesMissingTrustedAttachmentAssociation() throws Exception {
        long homeworkId = createPublishedFileHomework(114L);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission
                    (homework_id, student_id, submit_type, submit_status, evaluation_status,
                     review_status, version, is_final, submitted_at, created_at, updated_at, is_deleted)
                VALUES (?, 601, 'FILE', 'SUBMITTED', 'NONE', 'UNREVIEWED', 1, TRUE, ?, ?, ?, FALSE)
                """,
                homeworkId,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        long submissionId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_submission", Long.class);

        mockMvc.perform(get(
                        "/api/v1/homeworks/{homeworkId}/submissions/{submissionId}/attachment/download",
                        homeworkId,
                        submissionId
                ).headers(studentHeaders("114", "601")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HWK_4042"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void storageAndDatabaseFailuresDoNotLeaveUntrackedPhysicalUploads() throws Exception {
        long homeworkId = createPublishedFileHomework(108L);

        storage.failNextStore();
        upload(homeworkId, studentHeaders("108", "601"), "answer.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("HWK_5002"));
        assertThat(countAttachments(homeworkId)).isZero();
        assertThat(storage.size()).isZero();

        jdbcTemplate.execute("""
                ALTER TABLE t_hwk_submission_attachment
                    ADD CONSTRAINT ck_hwk_attachment_force_insert_failure CHECK (file_size < 0)
                """);
        try {
            upload(homeworkId, studentHeaders("108", "601"), "rollback.pdf", "application/pdf", VALID_PDF)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("HWK_5002"));
        } finally {
            jdbcTemplate.execute("""
                    ALTER TABLE t_hwk_submission_attachment
                        DROP CONSTRAINT ck_hwk_attachment_force_insert_failure
                    """);
        }

        assertThat(countAttachments(homeworkId)).isZero();
        assertThat(storage.size()).isZero();
    }

    @Test
    void failedDatabaseInsertAndImmediateDeletePersistCleanupUntilRetrySucceeds() throws Exception {
        long homeworkId = createPublishedFileHomework(118L);

        jdbcTemplate.execute("""
                ALTER TABLE t_hwk_submission_attachment
                    ADD CONSTRAINT ck_hwk_attachment_force_deferred_cleanup CHECK (file_size < 0)
                """);
        storage.failNextDelete();
        try {
            upload(homeworkId, studentHeaders("118", "601"), "orphan.pdf", "application/pdf", VALID_PDF)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("HWK_5002"));
        } finally {
            jdbcTemplate.execute("""
                    ALTER TABLE t_hwk_submission_attachment
                        DROP CONSTRAINT ck_hwk_attachment_force_deferred_cleanup
                    """);
        }

        assertThat(countAttachments(homeworkId)).isZero();
        assertThat(storage.size()).isOne();
        assertThat(storage.pendingDeleteCount()).isOne();

        invokeCleanup();

        assertThat(storage.size()).isZero();
        assertThat(storage.pendingDeleteCount()).isZero();
    }

    @Test
    void orphanCleanupDeletesOnlyExpiredUnboundUploadsAndRetriesStorageFailures() throws Exception {
        long homeworkId = createPublishedFileHomework(109L);
        String expiredId = uploadAndReturnId(homeworkId, studentHeaders("109", "601"));
        String freshId = uploadAndReturnId(homeworkId, studentHeaders("109", "602"));
        jdbcTemplate.update(
                "UPDATE t_hwk_submission_attachment SET expires_at = ? WHERE public_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                expiredId
        );
        String expiredStorageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                expiredId
        );
        String freshStorageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                freshId
        );

        storage.failNextDelete();
        invokeCleanup();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                expiredId
        )).isEqualTo("DELETED");
        assertThat(storage.contains(expiredStorageKey)).isTrue();

        invokeCleanup();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_hwk_submission_attachment WHERE public_id = ?",
                Integer.class,
                expiredId
        )).isZero();
        assertThat(storage.contains(expiredStorageKey)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_hwk_submission_attachment WHERE public_id = ?",
                String.class,
                freshId
        )).isEqualTo("UPLOADED");
        assertThat(storage.contains(freshStorageKey)).isTrue();

        int deleteAttemptsAfterCleanup = storage.deleteAttempts();
        invokeCleanup();
        assertThat(storage.deleteAttempts()).isEqualTo(deleteAttemptsAfterCleanup);
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            long homeworkId,
            HttpHeaders headers,
            String filename,
            String contentType,
            byte[] bytes
    ) throws Exception {
        return mockMvc.perform(multipart("/api/v1/homeworks/{homeworkId}/attachments", homeworkId)
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .headers(headers));
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            long homeworkId,
            HttpHeaders headers,
            Map<String, Object> payload
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private String uploadAndReturnId(long homeworkId, HttpHeaders headers) throws Exception {
        String body = upload(homeworkId, headers, "作业答案.pdf", "application/pdf", VALID_PDF)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("fileId").asText();
    }

    private long submitAndReturnId(long homeworkId, String fileId, HttpHeaders headers) throws Exception {
        String body = submit(homeworkId, headers, Map.of("fileIds", java.util.List.of(fileId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("submissionId").asLong();
    }

    private long createPublishedFileHomework(long courseId) {
        return createPublishedHomework(courseId, "FILE");
    }

    private long createPublishedHomework(long courseId, String type) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO t_hwk_homework
                    (course_id, title, type, status, total_score, deadline, allow_resubmit,
                     allow_late_submit, show_evaluation_before_publish, created_by, published_at,
                     is_deleted, created_at, updated_at)
                VALUES (?, 'HWK FILE RED', ?, 'PUBLISHED', 100, ?, TRUE, FALSE, FALSE,
                        501, ?, FALSE, ?, ?)
                """,
                courseId,
                type,
                Timestamp.valueOf(now.plusDays(2)),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM t_hwk_homework", Long.class);
    }

    private int countAttachments(long homeworkId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_hwk_submission_attachment WHERE homework_id = ?",
                    Integer.class,
                    homeworkId
            );
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void invokeCleanup() throws Exception {
        Object cleanupService = applicationContext.getBean("homeworkAttachmentCleanupService");
        Method cleanup = cleanupService.getClass().getDeclaredMethod("cleanupExpiredUploads");
        cleanup.setAccessible(true);
        cleanup.invoke(cleanupService);
    }

    private void deleteIfExists(String sql) {
        try {
            jdbcTemplate.update(sql);
        } catch (RuntimeException ignored) {
            // The first RED run intentionally precedes the attachment migration.
        }
    }

    private HttpHeaders studentHeaders(String courseIds, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", userId);
        headers.add("X-User-Role", "STUDENT");
        headers.add("X-Course-Ids", courseIds);
        return headers;
    }

    private HttpHeaders teacherHeaders(String courseIds, String manageableCourseIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Course-Ids", courseIds);
        headers.add("X-Manageable-Course-Ids", manageableCourseIds);
        return headers;
    }

    static final class RecordingFileStorageService implements FileStorageService {
        private final Map<String, StoredAsset> files = new ConcurrentHashMap<>();
        private final java.util.Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();
        private volatile boolean failStore;
        private volatile boolean failDelete;
        private volatile CyclicBarrier storeBarrier;
        private int deleteAttempts;

        @Override
        public StoredFile store(String filename, String contentType, InputStream content) {
            if (failStore) {
                failStore = false;
                throw new IllegalStateException("storage write unavailable");
            }
            try {
                byte[] bytes = content.readAllBytes();
                String key = "homework/" + UUID.randomUUID();
                files.put(key, new StoredAsset(filename, contentType, bytes));
                CyclicBarrier barrier = storeBarrier;
                if (barrier != null) {
                    barrier.await(5, TimeUnit.SECONDS);
                }
                return storedFile(key, files.get(key));
            } catch (Exception exception) {
                throw new IllegalStateException("storage write unavailable", exception);
            }
        }

        @Override
        public StoredFile load(String storageKey) {
            StoredAsset asset = files.get(storageKey);
            if (asset == null) {
                throw new IllegalStateException("storage object missing");
            }
            return storedFile(storageKey, asset);
        }

        @Override
        public void delete(String storageKey) {
            deleteAttempts++;
            if (failDelete) {
                failDelete = false;
                throw new IllegalStateException("storage delete unavailable");
            }
            files.remove(storageKey);
        }

        void failNextStore() {
            failStore = true;
        }

        void failNextDelete() {
            failDelete = true;
        }

        void awaitConcurrentStores(int parties) {
            storeBarrier = new CyclicBarrier(parties);
        }

        boolean contains(String storageKey) {
            return files.containsKey(storageKey);
        }

        int size() {
            return files.size();
        }

        int deleteAttempts() {
            return deleteAttempts;
        }

        @Override
        public void deferDelete(String storageKey) {
            pendingDeletes.add(storageKey);
        }

        @Override
        public java.util.List<String> pendingDeletes(int limit) {
            return pendingDeletes.stream().sorted().limit(limit).toList();
        }

        @Override
        public void completeDeferredDelete(String storageKey) {
            pendingDeletes.remove(storageKey);
        }

        int pendingDeleteCount() {
            return pendingDeletes.size();
        }

        void reset() {
            files.clear();
            pendingDeletes.clear();
            failStore = false;
            failDelete = false;
            storeBarrier = null;
            deleteAttempts = 0;
        }

        private StoredFile storedFile(String key, StoredAsset asset) {
            byte[] copy = Arrays.copyOf(asset.bytes(), asset.bytes().length);
            ByteArrayResource resource = new ByteArrayResource(copy) {
                @Override
                public String getFilename() {
                    return asset.filename();
                }
            };
            return new StoredFile(
                    key,
                    asset.filename(),
                    asset.contentType(),
                    copy.length,
                    "file:///must-never-leak/" + key,
                    resource
            );
        }

        private record StoredAsset(String filename, String contentType, byte[] bytes) {
        }
    }

    private byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record ArchiveCase(String filename, String contentType, String requiredEntry) {
    }
}
