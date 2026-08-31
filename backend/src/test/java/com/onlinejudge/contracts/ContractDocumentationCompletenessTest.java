package com.onlinejudge.contracts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五服务跨服务契约文档完整性：现行文档只允许一个五服务边界，
 * 并必须明确身份、可靠事件、失败关闭和恢复语义。
 */
class ContractDocumentationCompletenessTest {

    private static final Path CONTRACT_DOC = contractDocumentPath();

    @Test
    void currentContractFreezesExactlyTheFiveServiceBoundary() throws Exception {
        assertThat(CONTRACT_DOC).isRegularFile();
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);

        assertThat(content)
                .contains("Identity", "Course", "Assessment", "Grade", "Learning")
                .contains("Assessment 的 API 与 Worker 是一个服务的两个 workload")
                .doesNotContain("D4-CROSS-SERVICE-共享契约.md")
                .doesNotContain("学习与成绩服务");
    }

    @Test
    void currentContractDefinesBoundedFailureAndRecovery() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);

        assertThat(content)
                .contains("at-least-once")
                .contains("transactional outbox/inbox")
                .contains("DLQ")
                .contains("replay/reconciliation")
                .contains("COURSE_AUTHORIZATION_UNAVAILABLE")
                .contains("Learning 或 broker 不可用不回滚");
    }

    @Test
    void currentContractHasAnIndependentExecutableVerifier() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);
        assertThat(content).contains("verify-microservice-contract-v2.mjs");
    }

    private static Path contractDocumentPath() {
        String relative = "docs/开发/D6-D7-五服务共享契约-v2.md";
        Path direct = Path.of(relative);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        return Path.of("..", relative);
    }
}
