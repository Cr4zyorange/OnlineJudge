package com.onlinejudge.contracts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三业务服务跨服务契约文档完整性：现行文档只允许一个三业务服务边界，
 * 并必须明确身份、可靠事件、失败关闭和恢复语义。
 */
class ContractDocumentationCompletenessTest {

    private static final Path CONTRACT_DOC = contractDocumentPath();

    @Test
    void currentContractFreezesExactlyTheThreeBusinessServiceBoundary() throws Exception {
        assertThat(CONTRACT_DOC).isRegularFile();
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);

        assertThat(content)
                .contains("Identity", "Course", "Assessment", "Grade")
                .contains("Assessment 向 Grade 发布")
                .doesNotContain("D4-CROSS-SERVICE-共享契约.md")
                .doesNotContain("五服务");
    }

    @Test
    void currentContractDefinesBoundedFailureAndRecovery() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);

        assertThat(content)
                .contains("at-least-once")
                .contains("DLQ")
                .contains("replay/reconciliation")
                .contains("Course 不可用时返回 503")
                .contains("RabbitMQ/Course 不可用不回滚发布");
    }

    @Test
    void currentContractHasAnIndependentExecutableVerifier() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);
        assertThat(content).contains("verify-three-service-baseline-306.mjs");
    }

    private static Path contractDocumentPath() {
        String relative = "docs/开发/D6-三服务共享契约-306.md";
        Path direct = Path.of(relative);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        return Path.of("..", relative);
    }
}
