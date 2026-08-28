package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSchemaOwnershipTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path OWNED_MIGRATION =
            RESOURCES.resolve("db/migration/DB-AUTH-01-auth-user-session.sql");

    @Test
    void serviceOwnsOnlyTheSevenAuthTables() throws IOException {
        assertThat(OWNED_MIGRATION).isRegularFile();

        List<Path> sqlFiles;
        try (var files = Files.walk(RESOURCES)) {
            sqlFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList();
        }
        String sql = sqlFiles.stream()
                .map(this::read)
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(sql).contains(
                "t_auth_user",
                "t_auth_role",
                "t_auth_permission",
                "t_auth_user_role",
                "t_auth_role_permission",
                "t_auth_session",
                "t_auth_audit_log"
        );
        assertThat(sql).doesNotContain(
                "crs_",
                "lab_",
                "t_hwk_",
                "lrn_",
                "t_grade_",
                "t_course_grade_summary"
        );
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}
