package com.onlinejudge.integration.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #310 C-06 配置层与执行模型契约：`onlinejudge.integration.grade.timeout-ms` 必须由
 * Spring 绑定到生产 Bean（默认 1000ms），超时/中断必须取消后台任务（不能遗留
 * 永不结束的 Provider 调用），执行模型必须有并发/队列上限，饱和时快速失败。
 */
class GradeTimeoutConfigurationTest {

    private static CountDownLatch blockLatch = new CountDownLatch(1);
    private static CountDownLatch startedLatch = new CountDownLatch(1);
    private static final AtomicBoolean INTERRUPTED = new AtomicBoolean(false);

    @BeforeEach
    void resetState() {
        blockLatch = new CountDownLatch(1);
        startedLatch = new CountDownLatch(1);
        INTERRUPTED.set(false);
    }

    @Test
    void nonDefaultTimeoutIsBoundAndCancelsTheBackGroundTask() {
        new ApplicationContextRunner()
                .withUserConfiguration(GradeClientConfig.class)
                .withPropertyValues("onlinejudge.integration.grade.timeout-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DefaultSourceGradeClient client = context.getBean(DefaultSourceGradeClient.class);

                    long startedAt = System.nanoTime();
                    assertThatThrownBy(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 301L))
                            .isInstanceOf(DefaultSourceGradeClient.SourceGradeUnavailableException.class)
                            .hasMessageContaining("timed out after 50ms");
                    long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

                    assertThat(elapsedMs)
                            .as("50ms 配置必须生效（默认 1000ms 时该用例约需 1s）")
                            .isLessThan(500L);
                    awaitInterrupted("超时后必须取消后台 Provider 任务");
                });
    }

    @Test
    void saturatedExecutorFailsFastInsteadOfAccumulatingBackgroundTasks() {
        new ApplicationContextRunner()
                .withUserConfiguration(GradeClientConfig.class)
                .withPropertyValues(
                        "onlinejudge.integration.grade.timeout-ms=500",
                        "onlinejudge.integration.grade.max-pool-size=1",
                        "onlinejudge.integration.grade.queue-capacity=0"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DefaultSourceGradeClient client = context.getBean(DefaultSourceGradeClient.class);

                    Thread firstCall = new Thread(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 301L));
                    firstCall.start();
                    assertThat(awaitStarted())
                            .as("第一个调用应占用唯一工作线程")
                            .isTrue();

                    assertThatThrownBy(() -> client.findSourceGrades(101L, SourceGradeType.LAB, 302L))
                            .isInstanceOf(DefaultSourceGradeClient.SourceGradeUnavailableException.class)
                            .hasMessageContaining("saturated");

                    blockLatch.countDown();
                    firstCall.join(2000);
                    assertThat(firstCall.isAlive())
                            .as("释放后第一个调用必须完成退出")
                            .isFalse();
                });
    }

    @Test
    void nonPositiveConfiguredTimeoutFailsContextStartupFast() {
        new ApplicationContextRunner()
                .withUserConfiguration(GradeClientConfig.class)
                .withPropertyValues("onlinejudge.integration.grade.timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "onlinejudge.integration.grade.timeout-ms must be a positive value, got 0"
                            );
                });
    }

    @Test
    void nonPositiveExecutorBoundsFailContextStartupFast() {
        new ApplicationContextRunner()
                .withUserConfiguration(GradeClientConfig.class)
                .withPropertyValues(
                        "onlinejudge.integration.grade.max-pool-size=0",
                        "onlinejudge.integration.grade.queue-capacity=1"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("onlinejudge.integration.grade.max-pool-size must be positive, got 0");
                });

        new ApplicationContextRunner()
                .withUserConfiguration(GradeClientConfig.class)
                .withPropertyValues(
                        "onlinejudge.integration.grade.max-pool-size=1",
                        "onlinejudge.integration.grade.queue-capacity=-1"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "onlinejudge.integration.grade.queue-capacity must not be negative, got -1"
                            );
                });
    }

    @Test
    void constructorRejectsInvalidDurationAndBounds() {
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ofSeconds(1), 0, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-pool-size");
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ofSeconds(1), 4, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue-capacity");
    }

    private static void awaitInterrupted(String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!INTERRUPTED.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for cancellation", exception);
            }
        }
        assertThat(INTERRUPTED).as(description).isTrue();
    }

    private static boolean awaitStarted() {
        try {
            return startedLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DefaultSourceGradeClient.class)
    static class GradeClientConfig {
        @Bean
        SourceGradeProvider blockingProvider() {
            return new SourceGradeProvider() {
                @Override
                public boolean supports(SourceGradeType sourceType) {
                    return true;
                }

                @Override
                public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
                    startedLatch.countDown();
                    try {
                        blockLatch.await();
                    } catch (InterruptedException exception) {
                        INTERRUPTED.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return Optional.of(List.of());
                }
            };
        }
    }
}
