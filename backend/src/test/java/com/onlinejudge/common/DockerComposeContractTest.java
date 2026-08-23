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
        assertThat(compose).contains("/api/v1/system/health");
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
}
