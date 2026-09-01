package com.onlinejudge.gradeservice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GradeDeploymentArtifactTest {
    @Test
    void productionMigrationAndImageContainTheCompleteIndependentGradeRuntime() throws Exception {
        String migration = Files.readString(Path.of("../../database/migrations/grade/V20260901_02__complete_grade_runtime.sql"));
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS t_grade_item")
                .contains("CREATE TABLE IF NOT EXISTS t_grade_record")
                .contains("CREATE TABLE IF NOT EXISTS grade_event_outbox")
                .contains("CREATE TABLE IF NOT EXISTS grade_result_trace")
                .contains("grade_source_projection_gap")
                .contains("source_status");

        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile)
                .contains("USER 10004:10004")
                .contains("EXPOSE 8084")
                .contains("backend/src/main/java")
                .doesNotContain("GRADE_DATABASE_PASSWORD");
    }
}
