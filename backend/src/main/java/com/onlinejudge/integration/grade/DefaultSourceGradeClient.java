package com.onlinejudge.integration.grade;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Primary
@Component
public class DefaultSourceGradeClient implements SourceGradeClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1000);
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final int DEFAULT_QUEUE_CAPACITY = 64;

    private final List<SourceGradeProvider> providers;
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    @Autowired
    public DefaultSourceGradeClient(
            List<SourceGradeProvider> providers,
            @Value("${onlinejudge.integration.grade.timeout-ms:1000}") long timeoutMs,
            @Value("${onlinejudge.integration.grade.max-pool-size:4}") int maxPoolSize,
            @Value("${onlinejudge.integration.grade.queue-capacity:64}") int queueCapacity
    ) {
        this(providers, requirePositiveTimeout(timeoutMs), maxPoolSize, queueCapacity);
    }

    public DefaultSourceGradeClient(List<SourceGradeProvider> providers) {
        this(providers, DEFAULT_TIMEOUT);
    }

    public DefaultSourceGradeClient(List<SourceGradeProvider> providers, Duration timeout) {
        this(providers, timeout, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    public DefaultSourceGradeClient(
            List<SourceGradeProvider> providers,
            Duration timeout,
            int maxPoolSize,
            int queueCapacity
    ) {
        this.providers = providers;
        this.timeout = requirePositiveTimeout(timeout);
        this.executor = new ThreadPoolExecutor(
                requirePositivePoolSize(maxPoolSize),
                requirePositivePoolSize(maxPoolSize),
                60,
                TimeUnit.SECONDS,
                queueCapacity == 0
                        ? new SynchronousQueue<>()
                        : new ArrayBlockingQueue<>(requireNonNegativeQueueCapacity(queueCapacity)),
                Thread.ofPlatform().daemon(true).name("source-grade-timeout-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        for (SourceGradeProvider provider : providers) {
            if (!provider.supports(sourceType)) {
                continue;
            }
            Future<Optional<List<SourceGradeDTO>>> future;
            try {
                future = executor.submit(() -> provider.findSourceGrades(courseId, sourceId));
            } catch (RejectedExecutionException exception) {
                throw new SourceGradeUnavailableException(
                        "source grade provider saturated (maxPoolSize=" + executor.getPoolSize()
                                + ", queueCapacity=" + executor.getQueue().remainingCapacity() + ") for "
                                + sourceType + " sourceId=" + sourceId,
                        exception
                );
            }
            try {
                Optional<List<SourceGradeDTO>> sourceGrades = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (sourceGrades.isPresent()) {
                    return sourceGrades.get();
                }
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new SourceGradeUnavailableException(
                        "source grade provider timed out after " + timeout.toMillis()
                                + "ms for " + sourceType + " sourceId=" + sourceId,
                        exception
                );
            } catch (ExecutionException exception) {
                future.cancel(true);
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new SourceGradeUnavailableException(
                        "source grade provider failed for " + sourceType + " sourceId=" + sourceId,
                        cause
                );
            } catch (InterruptedException exception) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new SourceGradeUnavailableException(
                        "source grade lookup interrupted for " + sourceType + " sourceId=" + sourceId,
                        exception
                );
            }
        }
        return List.of();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static Duration requirePositiveTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.grade.timeout-ms must be a positive value, got " + timeoutMs
            );
        }
        return Duration.ofMillis(timeoutMs);
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("source grade timeout must be a positive duration");
        }
        return timeout;
    }

    private static int requirePositivePoolSize(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.grade.max-pool-size must be positive, got " + maxPoolSize
            );
        }
        return maxPoolSize;
    }

    private static int requireNonNegativeQueueCapacity(int queueCapacity) {
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.grade.queue-capacity must not be negative, got " + queueCapacity
            );
        }
        return queueCapacity;
    }

    /**
     * #310 C-06 下游不可用/超时/饱和的显式失败类型：GRD 必须原子中止本次成绩同步，
     * 不得把超时静默当作 MISSING 生成部分或不可解释结果。
     */
    public static final class SourceGradeUnavailableException extends RuntimeException {
        public SourceGradeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
