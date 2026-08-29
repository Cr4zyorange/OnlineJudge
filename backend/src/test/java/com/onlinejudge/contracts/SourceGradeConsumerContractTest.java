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
    void consumerPropagatesDownstreamFailureSoGradeSyncCanAbortAtomically() {
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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source service unavailable");
    }

    @Test
    void consumerTimesOutAndAbortsWithoutPartialResults() {
        CountDownLatch neverRelease = new CountDownLatch(1);
        SourceGradeProvider blockingProvider = new SourceGradeProvider() {
            @Override
            public boolean supports(SourceGradeType sourceType) {
                return true;
            }

            @Override
            public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                try {
                    neverRelease.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return Optional.of(List.of());
            }
        };
        DefaultSourceGradeClient client = new DefaultSourceGradeClient(
                List.of(blockingProvider), Duration.ofMillis(50)
        );

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 301L))
                .isInstanceOf(DefaultSourceGradeClient.SourceGradeUnavailableException.class)
                .hasMessageContaining("timed out");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertThat(elapsedMs).isLessThan(5000L);
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
