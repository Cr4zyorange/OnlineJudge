package com.onlinejudge.lab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @BeforeEach
    void cleanTables() {
        deleteIfExists("DELETE FROM lab_evaluation_result");
        deleteIfExists("DELETE FROM lab_evaluation");
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
                .andExpect(jsonPath("$.data.caseResults", hasSize(2)))
                .andExpect(jsonPath("$.data.caseResults[1].input").doesNotExist())
                .andExpect(jsonPath("$.data.caseResults[1].expectedOutput").doesNotExist());

        mockMvc.perform(get("/api/v1/labs/{labId}/submissions/{submissionId}/result", labId, submissionId)
                        .headers(teacherHeaders("513", "513")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluationStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.caseResults[1].input").value("2 3"))
                .andExpect(jsonPath("$.data.caseResults[1].expectedOutput").value("sum:5"));

        Integer aggregateRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lab_evaluation WHERE submission_id = ?",
                Integer.class,
                submissionId
        );
        org.assertj.core.api.Assertions.assertThat(aggregateRows).isEqualTo(1);
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
