package com.onlinejudge.integration.grade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #310 C-06 配置层契约：`onlinejudge.integration.grade.timeout-ms` 必须由 Spring
 * 绑定到生产 Bean（默认 1000ms），非默认值必须实际生效，非法值必须快速失败。
 */
class GradeTimeoutConfigurationTest {

    private static final CountDownLatch NEVER_RELEASE = new CountDownLatch(1);

    @Test
    void nonDefaultTimeoutIsBoundFromSpringConfiguration() {
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
    void constructorRejectsNonPositiveDuration() {
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new DefaultSourceGradeClient(List.of(), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
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
                    try {
                        NEVER_RELEASE.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return Optional.of(List.of());
                }
            };
        }
    }
}
