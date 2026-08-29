package com.onlinejudge.contracts;

import com.onlinejudge.integration.grade.DefaultSourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeProvider;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #310 C-06 来源成绩消费端契约（可独立运行）：GRD 只通过 SourceGradeClient 消费，
 * 下游失败/超时原子中止，不产生部分结果。
 */
class SourceGradeConsumerContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    @Test
    void consumerRoutesToMatchingProviderAndReturnsFrozenDtos() {
        SourceGradeProvider labProvider = new SourceGradeProvider() {
            @Override
            public boolean supports(SourceGradeType sourceType) {
                return sourceType == SourceGradeType.LAB;
            }

            @Override
            public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                return Optional.of(List.of(new SourceGradeDTO(
                        101L, SourceGradeType.LAB, 301L, 601L, new BigDecimal("85"),
                        new BigDecimal("100"), "SCORED", NOW
                )));
            }
        };
        DefaultSourceGradeClient client = new DefaultSourceGradeClient(List.of(labProvider));

        assertThat(client.findSourceGrades(101L, SourceGradeType.LAB, 301L)).hasSize(1);
        assertThat(client.findSourceGrades(101L, SourceGradeType.HWK, 301L)).isEmpty();
    }

    @Test
    void consumerUnifiesDownstreamFailureAsSourceGradeUnavailable() {
        SourceGradeProvider brokenProvider = new SourceGradeProvider() {
            @Override
            public boolean supports(SourceGradeType sourceType) {
                return true;
            }

            @Override
            public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                throw new IllegalStateException("source service unavailable");
            }
        };
        DefaultSourceGradeClient client = new DefaultSourceGradeClient(List.of(brokenProvider));

        assertThatThrownBy(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 301L))
                .isInstanceOf(DefaultSourceGradeClient.SourceGradeUnavailableException.class)
                .hasMessageContaining("source grade provider failed")
                .hasRootCauseMessage("source service unavailable");
    }

    @Test
    void consumerTimesOutAndAbortsWithoutPartialResults() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        SourceGradeProvider blockingProvider = new SourceGradeProvider() {
            @Override
            public boolean supports(SourceGradeType sourceType) {
                return true;
            }

            @Override
            public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                try {
                    blockLatch.await();
                } catch (InterruptedException exception) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
        };
        DefaultSourceGradeClient client = new DefaultSourceGradeClient(
                List.of(blockingProvider), Duration.ofMillis(50)
        );

        try {
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 301L))
                    .isInstanceOf(DefaultSourceGradeClient.SourceGradeUnavailableException.class)
                    .hasMessageContaining("timed out");
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            assertThat(elapsedMs).isLessThan(5000L);

            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!interrupted.get() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(interrupted)
                    .as("超时后后台 Provider 任务必须被取消，不能遗留永不结束的调用")
                    .isTrue();
        } finally {
            client.shutdown();
        }
    }

    @Test
    void duplicateQueriesAreStable() {
        SourceGradeProvider provider = new SourceGradeProvider() {
            @Override
            public boolean supports(SourceGradeType sourceType) {
                return sourceType == SourceGradeType.LAB;
            }

            @Override
            public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                return Optional.of(List.of(new SourceGradeDTO(
                        courseId, SourceGradeType.LAB, sourceId, 601L, new BigDecimal("85"),
                        new BigDecimal("100"), "SCORED", NOW
                )));
            }
        };
        DefaultSourceGradeClient client = new DefaultSourceGradeClient(List.of(provider));

        assertThat(client.findSourceGrades(101L, SourceGradeType.LAB, 301L))
                .isEqualTo(client.findSourceGrades(101L, SourceGradeType.LAB, 301L));
    }
}
