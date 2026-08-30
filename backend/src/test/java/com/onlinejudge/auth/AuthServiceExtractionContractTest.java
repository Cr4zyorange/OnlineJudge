package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceExtractionContractTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();
    private static final Path SERVICE = REPOSITORY.resolve("services/identity");

    @Test
    void identityHasIndependentBuildAndApplication() {
        assertThat(SERVICE.resolve("pom.xml")).isRegularFile();
        assertThat(SERVICE.resolve("src/main/java/com/onlinejudge/identityservice/IdentityServiceApplication.java"))
                .isRegularFile();
    }

    @Test
    void identityHasIndependentHardenedContainerDeployment() throws IOException {
        Path dockerfilePath = REPOSITORY.resolve("services/identity/Dockerfile");
        Path cachedRuntimeDockerfile = REPOSITORY.resolve("services/identity/Dockerfile.cached-runtime");
        Path composePath = REPOSITORY.resolve("deploy/docker/compose.identity.yml");
        assertThat(dockerfilePath).isRegularFile();
        assertThat(cachedRuntimeDockerfile).isRegularFile();
        assertThat(composePath).isRegularFile();

        String dockerfile = Files.readString(dockerfilePath);
        assertThat(dockerfile).contains(
                "services/identity/pom.xml",
                "onlinejudge-identity-service-0.1.0-SNAPSHOT.jar",
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
        String cachedRuntime = Files.readString(cachedRuntimeDockerfile);
        assertThat(cachedRuntime).contains(
                "ARG RUNTIME_BASE",
                "FROM ${RUNTIME_BASE}",
                "USER 10001:10001",
                "onlinejudge-identity-service-0.1.0-SNAPSHOT.jar"
        );

        String compose = Files.readString(composePath);
        assertThat(compose).contains(
                "  identity-db:",
                "  identity-service:",
                "onlinejudge/identity-service:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}",
                "${IDENTITY_DATABASE_PASSWORD:?IDENTITY_DATABASE_PASSWORD is required}",
                "${IDENTITY_DATABASE_ROOT_PASSWORD:?IDENTITY_DATABASE_ROOT_PASSWORD is required}",
                "${IDENTITY_JWT_SIGNING_KEY:?IDENTITY_JWT_SIGNING_KEY is required}",
                "IDENTITY_JWT_PREVIOUS_PUBLIC_KEYS: ${IDENTITY_JWT_PREVIOUS_PUBLIC_KEYS:-}",
                "onlinejudge_identity",
                "IDENTITY_DATABASE_HOST: identity-db",
                "IDENTITY_DATABASE_NAME: onlinejudge_identity",
                "/api/v1/system/readiness"
        );
        assertThat(compose).doesNotContain(
                "  backend:",
                "  frontend:",
                "build:"
        );
    }
}
