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
        assertThat(compose).contains("${OJ_HTTP_PORT:-8088}:8080");
        assertThat(compose).contains("/api/v1/system/health");
    }

    @Test
    void composeRequiresFullGitShaForBothApplicationImages() throws IOException {
        Path composeFile = Path.of("..", "deploy", "docker", "compose.yml");
        String compose = Files.readString(composeFile);

        assertThat(compose).contains("image: ${BACKEND_IMAGE_REPOSITORY:-onlinejudge/backend}:${IMAGE_TAG:?IMAGE_TAG must be a full 40-character Git SHA}");
        assertThat(compose).contains("image: ${FRONTEND_IMAGE_REPOSITORY:-onlinejudge/frontend}:${IMAGE_TAG:?IMAGE_TAG must be a full 40-character Git SHA}");
        assertThat(compose).contains("IMAGE_REVISION: ${IMAGE_TAG:?IMAGE_TAG must be a full 40-character Git SHA}");
        assertThat(compose).contains("image: mysql:8.4");
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
        assertThat(ignores).contains("**/.env");
        assertThat(ignores).contains("**/*.pem");
        assertThat(ignores).contains("**/*.key");
    }

    @Test
    void backendRuntimeImageInstallsHealthcheckProbeTool() throws IOException {
        Path dockerfile = Path.of("..", "deploy", "docker", "backend.Dockerfile");
        String backendDockerfile = Files.readString(dockerfile);

        assertThat(backendDockerfile).contains("apt-get install -y --no-install-recommends wget");
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

        assertThat(backendDockerfile).contains("ARG IMAGE_REVISION");
        assertThat(frontendDockerfile).contains("ARG IMAGE_REVISION");
        assertThat(backendDockerfile).contains("org.opencontainers.image.revision=\"$IMAGE_REVISION\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.revision=\"$IMAGE_REVISION\"");
        assertThat(backendDockerfile).contains("org.opencontainers.image.version=\"$IMAGE_REVISION\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.version=\"$IMAGE_REVISION\"");
        assertThat(backendDockerfile).contains("org.opencontainers.image.source=\"$IMAGE_SOURCE\"");
        assertThat(frontendDockerfile).contains("org.opencontainers.image.source=\"$IMAGE_SOURCE\"");
        assertThat(backendDockerfile).contains("USER 10001:10001");
        assertThat(frontendDockerfile).contains("--mount=type=cache,target=/root/.npm");
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
