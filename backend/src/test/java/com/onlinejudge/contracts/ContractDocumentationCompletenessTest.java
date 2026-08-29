package com.onlinejudge.contracts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #310 文档完整性契约：契约正本中每条契约都必须完整覆盖 14 项属性，
 * 失败处理不得以孤立的“稍后重试”搪塞。
 */
class ContractDocumentationCompletenessTest {

    private static final Path CONTRACT_DOC = contractDocumentPath();

    private static final List<String> CONTRACT_IDS = List.of(
            "C-01", "C-02", "C-03", "C-04", "C-05", "C-06", "C-07"
    );

    private static final Set<String> REQUIRED_ATTRIBUTES = Set.of(
            "路径/事件名", "生产者", "消费者", "请求/载荷", "响应", "版本", "鉴权",
            "错误码", "超时", "幂等键", "重试", "补偿/降级", "日志", "兼容策略"
    );

    @Test
    void contractDocumentCoversAllSevenSeamsWithEveryRequiredAttribute() throws Exception {
        assertThat(CONTRACT_DOC).isRegularFile();
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);

        for (String contractId : CONTRACT_IDS) {
            String section = sectionOf(content, contractId);
            assertThat(section)
                    .as("契约 %s 必须有独立章节", contractId)
                    .isNotEmpty();
            for (String attribute : REQUIRED_ATTRIBUTES) {
                assertThat(section)
                        .as("契约 %s 必须说明 %s", contractId, attribute)
                        .contains(attribute);
            }
        }
    }

    @Test
    void failureMatrixNeverLeavesRetryUndefined() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);
        String failureMatrix = sectionOf(content, "失败处理矩阵");
        assertThat(failureMatrix).isNotEmpty();

        for (String contractId : CONTRACT_IDS) {
            String section = sectionOf(content, contractId);
            String retryLine = firstLineContaining(section, "重试");
            assertThat(retryLine)
                    .as("契约 %s 的重试策略必须明确（不允许只写“稍后重试”）", contractId)
                    .isNotEmpty();
            assertThat(retryLine).doesNotContain("稍后重试");
        }
    }

    @Test
    void contractTestsAreRunnableIndependentlyOnConsumerAndProducerSides() throws Exception {
        String content = Files.readString(CONTRACT_DOC, StandardCharsets.UTF_8);
        assertThat(content).contains("consumer");
        assertThat(content).contains("producer");
        assertThat(content).contains("contract-verify.sh");
    }

    private static String sectionOf(String content, String title) {
        String marker = "## " + title;
        int start = content.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int bodyStart = content.indexOf('\n', start) + 1;
        int next = content.indexOf("\n## ", bodyStart);
        return next < 0 ? content.substring(bodyStart) : content.substring(bodyStart, next);
    }

    private static String firstLineContaining(String content, String keyword) {
        return content.lines()
                .filter(line -> line.contains(keyword))
                .findFirst()
                .orElse("");
    }

    private static Path contractDocumentPath() {
        String relative = "docs/开发/D4-CROSS-SERVICE-共享契约.md";
        Path direct = Path.of(relative);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        return Path.of("..", relative);
    }
}
