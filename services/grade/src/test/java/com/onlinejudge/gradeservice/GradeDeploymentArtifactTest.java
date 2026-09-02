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

    @Test
    void productionMysqlUsesOnlyVersionedMigrationsAndHasALiveReadinessGate() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(application)
                .contains("mode: embedded")
                .doesNotContain("mode: always");

        Path liveAcceptance = Path.of("../../scripts/test/verify-grade-service-mysql-live.sh");
        assertThat(liveAcceptance).exists();
        String acceptance = Files.readString(liveAcceptance);
        assertThat(acceptance)
                .contains("V20260901_01__grade_service_schema.sql")
                .contains("V20260901_02__complete_grade_runtime.sql")
                .contains("/actuator/health/readiness")
                .contains("/health/ready")
                .doesNotContain("SPRING_SQL_INIT_MODE=");

        String backendGate = Files.readString(Path.of("../../scripts/ci/backend-verify.sh"));
        assertThat(backendGate).contains("verify-grade-service-mysql-live.sh");
    }
}
