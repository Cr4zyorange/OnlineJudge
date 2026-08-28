package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceExtractionContractTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();
    private static final Path SERVICE = REPOSITORY.resolve("services/auth-service");

    @Test
    void authHasIndependentBuildAndApplication() {
        assertThat(SERVICE.resolve("pom.xml")).isRegularFile();
        assertThat(SERVICE.resolve("src/main/java/com/onlinejudge/authservice/AuthServiceApplication.java"))
                .isRegularFile();
    }

    @Test
    void authHasIndependentHardenedContainerDeployment() throws IOException {
        Path dockerfilePath = REPOSITORY.resolve("deploy/docker/auth-service.Dockerfile");
        Path composePath = REPOSITORY.resolve("deploy/docker/compose.auth.yml");
        assertThat(dockerfilePath).isRegularFile();
        assertThat(composePath).isRegularFile();

        String dockerfile = Files.readString(dockerfilePath);
        assertThat(dockerfile).contains(
                "services/auth-service/pom.xml",
                "onlinejudge-auth-service-0.1.0-SNAPSHOT.jar",
                "EXPOSE 8081",
                "USER 10001:10001",
                "org.opencontainers.image.revision",
                "org.opencontainers.image.version",
                "org.opencontainers.image.source"
        );
        assertThat(dockerfile.lines().filter(line -> line.startsWith("FROM ")).toList())
                .hasSize(2)
                .allMatch(line -> line.contains("@sha256:"));
        assertThat(dockerfile).doesNotContain("apt-get");

        String compose = Files.readString(composePath);
        assertThat(compose).contains(
                "  auth-db:",
                "  auth-service:",
                "onlinejudge/auth-service:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}",
                "${AUTH_DB_PASSWORD:?AUTH_DB_PASSWORD is required}",
                "${AUTH_DB_ROOT_PASSWORD:?AUTH_DB_ROOT_PASSWORD is required}",
                "onlinejudge_auth",
                "AUTH_DB_HOST: auth-db",
                "AUTH_DB_NAME: onlinejudge_auth",
                "/api/v1/system/readiness"
        );
        assertThat(compose).doesNotContain(
                "  backend:",
                "  frontend:",
                "build:"
        );
    }
}
