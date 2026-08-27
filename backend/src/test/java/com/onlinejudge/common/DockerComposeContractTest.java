package com.onlinejudge.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeContractTest {
    @Test
    void mainComposeFileDefinesStableThreeServiceDeployment() throws IOException {
        Path composeFile = Path.of("..", "deploy", "docker", "compose.yml");
        String compose = Files.readString(composeFile);

        assertThat(Pattern.compile("(?m)^name: onlinejudge$").matcher(compose).find()).isTrue();
        assertThat(compose).contains("mysql:");
        assertThat(compose).contains("backend:");
        assertThat(compose).contains("frontend:");
        assertThat(compose).contains("mysql-data:/var/lib/mysql");
        assertThat(compose).contains("../../database/mysql/compose-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro");
        assertThat(compose).contains("${OJ_HTTP_PORT:-8088}:80");
        assertThat(compose).contains("/api/v1/system/readiness");
        assertThat(compose).doesNotContain("container_name:");
    }

    @Test
    void composeRequiresFullGitShaForBothApplicationImages() throws IOException {
        Path composeFile = Path.of("..", "deploy", "docker", "compose.yml");
        String compose = Files.readString(composeFile);

        assertThat(compose).contains("image: onlinejudge/backend:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}");
        assertThat(compose).contains("image: onlinejudge/frontend:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}");
        assertThat(compose).contains("GIT_SHA: ${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}");
        assertThat(compose).contains("MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}");
        assertThat(compose).contains("MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}");
        assertThat(compose).contains("image: mysql:8.4");
        assertThat(compose).doesNotContain("ONLINEJUDGE_NOTIFICATIONS_INTERNAL_TOKEN:");
        assertThat(compose).doesNotContain("image: latest");
    }

    @Test
    void backendBuildProducesExecutableSpringBootJar() throws IOException {
        Path pomFile = Path.of("pom.xml");
        String pom = Files.readString(pomFile);

        assertThat(pom).contains("<artifactId>spring-boot-maven-plugin</artifactId>");
    }

    @Test
    void mysqlBootstrapScriptAvoidsUnsupportedIndexIfNotExistsSyntax() throws IOException {
        Path bootstrapFile = Path.of("..", "database", "mysql", "compose-schema.sql");
        String bootstrap = Files.readString(bootstrapFile);

        assertThat(bootstrap).contains("CREATE TABLE IF NOT EXISTS t_auth_user");
        assertThat(bootstrap).contains("CREATE INDEX idx_auth_user_type");
        assertThat(bootstrap).doesNotContain("CREATE INDEX IF NOT EXISTS");
        assertThat(bootstrap).doesNotContain("CREATE UNIQUE INDEX IF NOT EXISTS");
    }

    @Test
    void nginxRoutesApiCallsToBackendService() throws IOException {
        Path nginxConfig = Path.of("..", "deploy", "nginx", "default.conf");
        String config = Files.readString(nginxConfig);

        assertThat(config).contains("client_max_body_size 55m;");
        assertThat(config).contains("location /api/");
        assertThat(config).contains("proxy_pass http://backend:8080;");
    }

    @Test
    void nginxServesSpaHistoryRoutesWithoutDirectoryRedirects() throws IOException {
        Path nginxConfig = Path.of("..", "deploy", "nginx", "default.conf");
        String config = Files.readString(nginxConfig);

        assertThat(config).contains("try_files $uri /index.html;");
        assertThat(config).doesNotContain("try_files $uri $uri/ /index.html;");
    }

    @Test
    void dockerBuildContextExcludesLocalBuildArtifacts() throws IOException {
        Path dockerIgnore = Path.of("..", ".dockerignore");
        String ignores = Files.readString(dockerIgnore);

        assertThat(ignores).contains(".git");
        assertThat(ignores).contains("backend/target");
        assertThat(ignores).contains("frontend/node_modules");
        assertThat(ignores).contains("frontend/dist");
        assertThat(ignores).contains("output");
        assertThat(ignores).contains("tmp");
        assertThat(ignores).contains("**/.env*");
        assertThat(ignores).contains("**/*.pem");
        assertThat(ignores).contains("**/*.key");
    }

    @Test
    void backendRuntimeImageUsesProbeFromPinnedBaseWithoutMutablePackageInstall() throws IOException {
        Path dockerfile = Path.of("..", "deploy", "docker", "backend.Dockerfile");
        String backendDockerfile = Files.readString(dockerfile);

        assertThat(backendDockerfile).contains("command -v wget");
        assertThat(backendDockerfile).doesNotContain("apt-get update");
        assertThat(backendDockerfile).doesNotContain("apt-get install");
    }

    @Test
    void backendImageBuildSkipsTestClasspathAndCachesMavenArtifacts() throws IOException {
        Path dockerfile = Path.of("..", "deploy", "docker", "backend.Dockerfile");
        String backendDockerfile = Files.readString(dockerfile);

        assertThat(backendDockerfile).contains("--mount=type=cache,target=/root/.m2");
        assertThat(backendDockerfile).contains("-Dmaven.test.skip=true package");
    }

    @Test
    void applicationImagesCarryOciRevisionAndRunAsNonRootUsers() throws IOException {
        String backendDockerfile = Files.readString(Path.of("..", "deploy", "docker", "backend.Dockerfile"));
        String frontendDockerfile = Files.readString(Path.of("..", "deploy", "docker", "frontend.Dockerfile"));

        assertThat(backendDockerfile).contains("FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build");
        assertThat(backendDockerfile).contains("FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037");
        assertThat(frontendDockerfile).contains("FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build");
        assertThat(frontendDockerfile).contains("FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10");
        assertThat(backendDockerfile).contains("ARG GIT_SHA");
        assertThat(frontendDockerfile).contains("ARG GIT_SHA");
        assertThat(backendDockerfile).contains("org.opencontainers.image.revision=\"$GIT_SHA\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.revision=\"$GIT_SHA\"");
        assertThat(backendDockerfile).contains("org.opencontainers.image.version=\"$GIT_SHA\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.version=\"$GIT_SHA\"");
        assertThat(backendDockerfile).contains("org.opencontainers.image.source=\"$IMAGE_SOURCE\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.source=\"$IMAGE_SOURCE\"");
        assertThat(backendDockerfile).contains("USER 10001:10001");
        assertThat(frontendDockerfile).contains("--mount=type=cache,target=/root/.npm");
        assertThat(frontendDockerfile).contains("pid /tmp/nginx.pid;");
        assertThat(frontendDockerfile).contains("USER nginx");
    }

    @Test
    void enhancedEvaluationUsesLinuxDockerCliImageAndOneSharedSandboxPath() throws IOException {
        Path composeOverride = Path.of("..", "deploy", "docker", "compose.eval.local.example.yml");
        Path dockerfile = Path.of("..", "deploy", "docker", "backend.eval.Dockerfile");
        String compose = Files.readString(composeOverride);
        String backendEvalDockerfile = Files.readString(dockerfile);

        assertThat(compose).contains("dockerfile: deploy/docker/backend.eval.Dockerfile");
        assertThat(compose).contains("JAVA_TOOL_OPTIONS: \"-Djava.io.tmpdir=${SANDBOX_WORKDIR:-/tmp/onlinejudge-sandbox}\"");
        assertThat(compose).contains("${SANDBOX_WORKDIR:-/tmp/onlinejudge-sandbox}:${SANDBOX_WORKDIR:-/tmp/onlinejudge-sandbox}");
        assertThat(compose).doesNotContain("DOCKER_CLI_PATH");
        assertThat(compose).doesNotContain("DOCKER_CLI_CONTAINER_PATH");

        assertThat(backendEvalDockerfile).contains("FROM docker:27.5.1-cli");
        assertThat(backendEvalDockerfile).contains("openjdk21-jre-headless");
        assertThat(backendEvalDockerfile).contains("COPY --from=build /workspace/backend/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar app.jar");
        assertThat(backendEvalDockerfile).contains("ENTRYPOINT [\"java\", \"-jar\", \"/opt/onlinejudge/app.jar\"");
    }
}
