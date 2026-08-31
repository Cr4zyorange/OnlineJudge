package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import com.onlinejudge.assessmentservice.security.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SourceGradeRebuildApiTest {
    private static final KeyPair KEY = TestJwtFactory.rsaKeyPair();
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SourceGradeRepository grades;

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("assessment.identity.jwks-trust-bundle", () -> TestJwtFactory.jwks("source-grade-kid", KEY));
        registry.add("assessment.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM assessment_source_grade_revision");
        jdbc.update("DELETE FROM assessment_source_grade_snapshot");
        jdbc.update("DELETE FROM assessment_source_grade");
    }

    @Test
    void gradeServiceCanReadStablePaginatedSourceFactsButUserBearerCannot() throws Exception {
        grades.upsertScored("HWK", "homework-8", "course-8", "student-1", new BigDecimal("91"), new BigDecimal("100"), Instant.parse("2026-08-31T00:00:00Z"));
        grades.upsertScored("HWK", "homework-8", "course-8", "student-2", new BigDecimal("88"), new BigDecimal("100"), Instant.parse("2026-08-31T00:00:01Z"));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-8").param("sourceType", "HWK").param("sourceId", "homework-8")
                        .header("X-Request-Id", "source-grade-rebuild")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + TestJwtFactory.serviceToken(KEY, "source-grade-kid", "assessment", List.of("grades:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceSnapshotVersion").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].studentId").value("student-1"));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-8").param("sourceType", "HWK").param("sourceId", "homework-8").param("page", "1").param("size", "1")
                        .header("X-Request-Id", "source-grade-second-page")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + TestJwtFactory.serviceToken(KEY, "source-grade-kid", "assessment", List.of("grades:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceSnapshotVersion").value(2)).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].studentId").value("student-2"));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-8").param("sourceType", "HWK").param("sourceId", "homework-8")
                        .header("X-Request-Id", "source-grade-user-token")
                        .header("Authorization", "Bearer " + TestJwtFactory.userToken(KEY, "source-grade-kid", "student-1", List.of("STUDENT"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-8").param("sourceType", "INVALID").param("sourceId", "homework-8")
                        .header("X-Request-Id", "source-grade-invalid-filter")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + TestJwtFactory.serviceToken(KEY, "source-grade-kid", "assessment", List.of("grades:read"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOURCE_GRADE_FILTER_INVALID"));
    }

    @Test
    void laterPagesReuseTheOriginalSnapshotWhenAnotherStudentGradeChanges() throws Exception {
        Instant start = Instant.parse("2026-08-31T00:00:00Z");
        grades.upsertScored("HWK", "homework-9", "course-9", "student-1", new BigDecimal("70"), new BigDecimal("100"), start);
        grades.upsertScored("HWK", "homework-9", "course-9", "student-2", new BigDecimal("80"), new BigDecimal("100"), start.plusSeconds(1));
        grades.upsertScored("HWK", "homework-9", "course-9", "student-1", new BigDecimal("95"), new BigDecimal("100"), start.plusSeconds(2));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-9").param("sourceType", "HWK").param("sourceId", "homework-9")
                        .param("snapshotVersion", "2").param("page", "0").param("size", "1")
                        .header("X-Request-Id", "source-grade-original-snapshot")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + TestJwtFactory.serviceToken(KEY, "source-grade-kid", "assessment", List.of("grades:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceSnapshotVersion").value(2))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].studentId").value("student-1"))
                .andExpect(jsonPath("$.items[0].score").value(70));

        mockMvc.perform(get("/internal/v2/source-grades").param("courseId", "course-9").param("sourceType", "HWK").param("sourceId", "homework-9")
                        .param("snapshotVersion", "2").param("page", "1").param("size", "1")
                        .header("X-Request-Id", "source-grade-original-snapshot-second-page")
                        .header("X-OnlineJudge-Service-Authorization", "Bearer " + TestJwtFactory.serviceToken(KEY, "source-grade-kid", "assessment", List.of("grades:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceSnapshotVersion").value(2))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].studentId").value("student-2"))
                .andExpect(jsonPath("$.items[0].score").value(80));
    }
}
