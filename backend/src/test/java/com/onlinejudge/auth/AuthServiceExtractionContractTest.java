package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;

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
}
