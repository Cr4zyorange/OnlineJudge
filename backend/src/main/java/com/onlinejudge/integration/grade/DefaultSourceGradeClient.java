package com.onlinejudge.integration.grade;

import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Primary
@Component
public class DefaultSourceGradeClient implements SourceGradeClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1000);

    private final List<SourceGradeProvider> providers;
    private final Duration timeout;
    private final ExecutorService timeoutExecutor;

    @Autowired
    public DefaultSourceGradeClient(List<SourceGradeProvider> providers) {
        this(providers, DEFAULT_TIMEOUT);
    }

    public DefaultSourceGradeClient(List<SourceGradeProvider> providers, Duration timeout) {
        this.providers = providers;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.timeoutExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        for (SourceGradeProvider provider : providers) {
            if (!provider.supports(sourceType)) {
                continue;
            }
            try {
                var sourceGrades = CompletableFuture
                        .supplyAsync(() -> provider.findSourceGrades(courseId, sourceId), timeoutExecutor)
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (sourceGrades.isPresent()) {
                    return sourceGrades.get();
                }
            } catch (TimeoutException exception) {
                throw new SourceGradeUnavailableException(
                        "source grade provider timed out after " + timeout.toMillis()
                                + "ms for " + sourceType + " sourceId=" + sourceId,
                        exception
                );
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new SourceGradeUnavailableException(
                        "source grade provider failed for " + sourceType + " sourceId=" + sourceId,
                        cause
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SourceGradeUnavailableException(
                        "source grade lookup interrupted for " + sourceType + " sourceId=" + sourceId,
                        exception
                );
            }
        }
        return List.of();
    }

    /**
     * #310 C-06 下游不可用/超时的显式失败类型：GRD 必须原子中止本次成绩同步，
     * 不得把超时静默当作 MISSING 生成部分或不可解释结果。
     */
    public static final class SourceGradeUnavailableException extends RuntimeException {
        public SourceGradeUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
