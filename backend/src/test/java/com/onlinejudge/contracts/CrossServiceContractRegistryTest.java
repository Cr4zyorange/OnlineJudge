package com.onlinejudge.contracts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五服务迁移期结构约束：保证消费方只能通过 contract 包访问其他服务数据。
 * 任何把 integration.course / integration.grade 消费端与生产者内部实现（mapper/repository/domain）
 * 重新耦合的改动都必须先在这里显式失败。
 */
class CrossServiceContractRegistryTest {

    private static final Path BACKEND_MAIN = Path.of("src/main/java/com/onlinejudge");
    private static final Path CONTRACT_DOC = contractDocumentPath();

    @Test
    void contractDocumentIsTheSingleSourceOfTruth() {
        assertThat(CONTRACT_DOC)
                .as("五服务 v2 契约正本必须存在")
                .isRegularFile();
    }

    private static Path contractDocumentPath() {
        String relative = "docs/开发/D6-D7-五服务共享契约-v2.md";
        Path direct = Path.of(relative);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        return Path.of("..", relative);
    }

    @Test
    void coursePermissionConsumerContractIsFrozenAtV1() throws Exception {
        Class<?> client = Class.forName("com.onlinejudge.integration.course.CoursePermissionClient");
        assertThat(client.isInterface()).isTrue();
        assertThat(versionOf(client)).isEqualTo("v1");

        Class<?> provider = Class.forName("com.onlinejudge.integration.course.CoursePermissionProvider");
        assertThat(provider.isInterface()).as("生产者 SPI CoursePermissionProvider 必须存在").isTrue();

        Class<?> defaultClient = Class.forName("com.onlinejudge.integration.course.DefaultCoursePermissionClient");
        assertThat(defaultClient.isInterface()).isFalse();
        assertThat(client.isAssignableFrom(defaultClient)).isTrue();
    }

    @Test
    void coursePermissionConsumerNeverReachesIntoCrsInternals() throws IOException {
        Path coursePackage = BACKEND_MAIN.resolve("integration/course");
        assertThat(coursePackage).isDirectory();
        try (var files = Files.walk(coursePackage)) {
            String sources = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> read(path))
                    .reduce("", String::concat);
            assertThat(sources)
                    .as("integration.course 消费端不得 import CRS 的 mapper/domain/repository")
                    .doesNotContain("com.onlinejudge.crs.mapper")
                    .doesNotContain("com.onlinejudge.crs.domain");
        }
    }

    @Test
    void sourceGradeContractIsFrozenAtV1() throws Exception {
        Class<?> dto = Class.forName("com.onlinejudge.integration.grade.SourceGradeDTO");
        assertThat(dto.isRecord()).isTrue();
        assertThat(versionOf(dto)).isEqualTo("v1");
        Set<String> fields = Arrays.stream(dto.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                "courseId", "sourceType", "sourceId", "studentId",
                "score", "fullScore", "status", "updatedAt"
        );
        assertThat(Class.forName("com.onlinejudge.integration.grade.SourceGradeClient").isInterface()).isTrue();
        assertThat(Class.forName("com.onlinejudge.integration.grade.SourceGradeProvider").isInterface()).isTrue();
        assertThat(Class.forName("com.onlinejudge.integration.grade.SourceGradeType").isEnum()).isTrue();
    }

    @Test
    void notificationEventContractIsFrozenAtV1() throws Exception {
        Class<?> event = Class.forName("com.onlinejudge.common.event.NotificationEvent");
        assertThat(event.isRecord()).isTrue();
        assertThat(versionOf(event)).isEqualTo("v1");
        Set<String> fields = Arrays.stream(event.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(fields).contains(
                "idempotencyKey", "type", "courseId", "recipientUserIds",
                "title", "content", "targetType", "targetId", "linkUrl", "occurredAt"
        );
        assertThat(Class.forName("com.onlinejudge.common.event.NotificationEventPublisher").isInterface()).isTrue();
    }

    @Test
    void evaluationContractIsFrozenAtV1WithStableStatusEnum() throws Exception {
        Class<?> task = Class.forName("com.onlinejudge.common.evaluation.EvaluationTask");
        Class<?> result = Class.forName("com.onlinejudge.common.evaluation.EvaluationResult");
        assertThat(task.isRecord()).isTrue();
        assertThat(result.isRecord()).isTrue();
        assertThat(versionOf(task)).isEqualTo("v1");
        assertThat(versionOf(result)).isEqualTo("v1");

        Class<?> status = Class.forName("com.onlinejudge.common.evaluation.EvaluationStatus");
        assertThat(Arrays.stream(status.getEnumConstants())
                .map(Object::toString)
                .toList()).containsExactly(
                "NONE", "PENDING", "RUNNING", "ACCEPTED", "WRONG_ANSWER",
                "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED", "SYSTEM_ERROR"
        );
    }

    @Test
    void authContextContractIsFrozenAtV1() throws Exception {
        Class<?> currentUser = Class.forName("com.onlinejudge.common.security.CurrentUser");
        assertThat(currentUser.isRecord()).isTrue();
        Set<String> fields = Arrays.stream(currentUser.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "role", "roles", "permissions");
        assertThat(Class.forName("com.onlinejudge.common.security.CurrentUserProvider").isInterface()).isTrue();
    }

    private static String versionOf(Class<?> type) throws Exception {
        Field version = type.getDeclaredField("VERSION");
        assertThat(Modifier.isStatic(version.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(version.getModifiers())).isTrue();
        return (String) version.get(null);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + path, exception);
        }
    }
}
