package com.onlinejudge.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBootstrapContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..");
    private static final Path DATABASE_ROOT = REPOSITORY_ROOT.resolve("database");

    @Test
    void migrationManifestListsEveryMigrationExactlyOnceInDependencyOrder() throws IOException {
        Path manifestPath = DATABASE_ROOT.resolve("migrations/manifest.txt");
        List<String> manifest = Files.readAllLines(manifestPath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        Set<String> migrationFiles;
        try (var files = Files.list(DATABASE_ROOT.resolve("migrations"))) {
            migrationFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertThat(manifest).doesNotHaveDuplicates();
        assertThat(new HashSet<>(manifest)).containsExactlyInAnyOrderElementsOf(migrationFiles);
        assertThat(manifest.indexOf("DB-AUTH-01-auth-user-session.sql"))
                .isLessThan(manifest.indexOf("DB-CRS-01-course-and-member.sql"));
        assertThat(manifest.indexOf("20260525_02_create_lab_experiment.sql"))
                .isLessThan(manifest.indexOf("20260526_01_create_lab_submission.sql"));
        assertThat(manifest.indexOf("20260530_01_create_hwk_homework.sql"))
                .isLessThan(manifest.indexOf("20260601_01_create_hwk_submission.sql"));
        assertThat(manifest.indexOf("20260825_01_add_grd_analysis_source_fingerprint.sql"))
                .isLessThan(manifest.indexOf("20260825_02_add_grd_analysis_source_version.sql"));
    }

    @Test
    void runnerRecordsChecksumsRejectsDriftAndNamesTheFailingMigration() throws IOException {
        Path runnerPath = DATABASE_ROOT.resolve("mysql/migrate.sh");
        String runner = Files.readString(runnerPath);

        assertThat(runnerPath).isExecutable();
        assertThat(runner)
                .contains("schema_migrations")
                .contains("checksum_sha256")
                .contains("manifest.txt")
                .contains("checksum mismatch")
                .contains("failed migration:")
                .contains("--baseline-through")
                .contains("--adapter")
                .contains("compose")
                .contains("docker")
                .contains("kubectl");
    }

    @Test
    void cleanSchemaCarriesAnAuditableMigrationBaseline() throws IOException {
        String cleanSchema = Files.readString(DATABASE_ROOT.resolve("mysql/compose-schema.sql"));

        assertThat(cleanSchema)
                .contains("CREATE TABLE schema_migrations")
                .contains("checksum_sha256 CHAR(64) NOT NULL")
                .contains("installed_type VARCHAR(16) NOT NULL")
                .contains("COMPOSE_BASELINE")
                .contains("20260825_02_add_grd_analysis_source_version.sql");
    }

    @Test
    void cleanSchemaBaselineChecksumsMatchEveryManifestMigration() throws Exception {
        List<String> manifest = manifestEntries();
        String cleanSchema = Files.readString(DATABASE_ROOT.resolve("mysql/compose-schema.sql"));
        Matcher matcher = Pattern.compile(
                "\\('([^']+\\.sql)', '([0-9a-f]{64})', 'COMPOSE_BASELINE'"
        ).matcher(cleanSchema);
        Map<String, String> baselineChecksums = new LinkedHashMap<>();
        while (matcher.find()) {
            baselineChecksums.put(matcher.group(1), matcher.group(2));
        }

        assertThat(baselineChecksums.keySet()).containsExactlyElementsOf(manifest);
        for (String migration : manifest) {
            assertThat(baselineChecksums.get(migration))
                    .as("checksum for %s", migration)
                    .isEqualTo(sha256(DATABASE_ROOT.resolve("migrations").resolve(migration)));
        }
    }

    @Test
    void devCiSeedIsExplicitIdentifiableNonLoginableAndRemovable() throws IOException {
        String seed = Files.readString(DATABASE_ROOT.resolve("seeds/dev-ci.sql"));
        String cleanup = Files.readString(DATABASE_ROOT.resolve("seeds/clean-dev-ci.sql"));

        assertThat(seed)
                .contains("DEV/CI ONLY")
                .contains("db_ci_student_287")
                .contains("db_ci_teacher_287")
                .contains("D3-DATABASE-287")
                .contains("DISABLED")
                .doesNotContain("Student001@pass", "Teacher001@pass", "Admin001@pass")
                .doesNotContain("VALUES(");
        assertThat(cleanup)
                .contains("db_ci_student_287")
                .contains("db_ci_teacher_287")
                .contains("D3-DATABASE-287")
                .contains("DELETE FROM crs_course_member")
                .contains("DELETE FROM t_auth_user");
    }

    @Test
    void composeAndMysql84VerifierConsumeTheDatabaseSourcesWithoutCopies() throws IOException {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("deploy/docker/compose.yml"));
        String verifier = Files.readString(DATABASE_ROOT.resolve("tests/verify-database-bootstrap.sh"));
        String assertions = Files.readString(DATABASE_ROOT.resolve("tests/assert-latest.sql"));

        assertThat(compose)
                .contains("../../database/mysql/compose-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro")
                .contains("../../database/seeds/dev-ci.sql:/docker-entrypoint-initdb.d/02-dev-ci-seed.sql:ro");
        assertThat(verifier)
                .contains("mysql:8.4")
                .contains("MySQL init process done. Ready for start up.")
                .contains("fresh")
                .contains("upgrade")
                .contains("migrate.sh")
                .contains("assert-latest.sql")
                .contains("--baseline-through")
                .contains("docker rm");
        assertThat(assertions)
                .contains("schema_migrations")
                .contains("db_ci_student_287")
                .contains("db_ci_teacher_287")
                .contains("D3-DATABASE-287")
                .contains("idx_hwk_submission_attention")
                .contains("fk_hwk_attachment_submission");
    }

    @Test
    void databaseContractDocumentsSeedBoundaryFailureAndRebuildRules() throws IOException {
        String contract = Files.readString(REPOSITORY_ROOT.resolve("docs/开发/D3-DATABASE-数据库启动与迁移契约.md"));

        assertThat(contract)
                .contains("空数据库")
                .contains("已有基线数据库")
                .contains("checksum")
                .contains("DEV/CI")
                .contains("IntDemoDataInitializer")
                .contains("不得删除持久化卷")
                .contains("DDL")
                .contains("回滚");
    }

    private List<String> manifestEntries() throws IOException {
        return Files.readAllLines(DATABASE_ROOT.resolve("migrations/manifest.txt")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
