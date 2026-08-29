package com.onlinejudge.integration.course;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * #310 C-03 课程权限消费端默认实现（v1）。
 * <p>DEV/演示环境允许网关以 {@code X-Course-Ids} / {@code X-Manageable-Course-Ids} 等请求头
 * 模拟 CRS 权限（受 {@code onlinejudge.auth.allow-header-auth} 开关约束）；正式路径通过
 * {@link CoursePermissionProvider} 消费 CRS 契约。每次调用都有有界超时预算：超时或下游
 * 不可用一律失败关闭（拒绝/空名单），并取消后台 Provider 任务；执行模型为有界线程池 +
 * 有界队列，饱和即拒绝，绝不回退到直连 CRS 表或实现。</p>
 */
@Component
public class DefaultCoursePermissionClient implements CoursePermissionClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultCoursePermissionClient.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1000);
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final int DEFAULT_QUEUE_CAPACITY = 64;

    private final boolean allowHeaderAuth;
    private final CoursePermissionProvider provider;
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    @Autowired
    public DefaultCoursePermissionClient(
            @Value("${onlinejudge.auth.allow-header-auth:false}") boolean allowHeaderAuth,
            CoursePermissionProvider provider,
            @Value("${onlinejudge.integration.course.timeout-ms:1000}") long timeoutMs,
            @Value("${onlinejudge.integration.course.max-pool-size:4}") int maxPoolSize,
            @Value("${onlinejudge.integration.course.queue-capacity:64}") int queueCapacity
    ) {
        this(allowHeaderAuth, provider, requirePositiveTimeout(timeoutMs), maxPoolSize, queueCapacity);
    }

    public DefaultCoursePermissionClient(boolean allowHeaderAuth, CoursePermissionProvider provider) {
        this(allowHeaderAuth, provider, DEFAULT_TIMEOUT);
    }

    public DefaultCoursePermissionClient(
            boolean allowHeaderAuth,
            CoursePermissionProvider provider,
            Duration timeout
    ) {
        this(allowHeaderAuth, provider, timeout, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    public DefaultCoursePermissionClient(
            boolean allowHeaderAuth,
            CoursePermissionProvider provider,
            Duration timeout,
            int maxPoolSize,
            int queueCapacity
    ) {
        this.allowHeaderAuth = allowHeaderAuth;
        this.provider = provider;
        this.timeout = requirePositiveTimeout(timeout);
        this.executor = new ThreadPoolExecutor(
                requirePositivePoolSize(maxPoolSize),
                requirePositivePoolSize(maxPoolSize),
                60,
                TimeUnit.SECONDS,
                queueCapacity == 0
                        ? new SynchronousQueue<>()
                        : new ArrayBlockingQueue<>(requireNonNegativeQueueCapacity(queueCapacity)),
                Thread.ofPlatform().daemon(true).name("course-permission-timeout-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public boolean courseExists(long courseId) {
        if (courseId <= 0) {
            return false;
        }
        return bounded(() -> provider.courseExists(courseId), false, courseId, 0, "courseExists");
    }

    @Override
    public boolean canManageCourse(long courseId, long userId) {
        if (courseId <= 0 || userId <= 0) {
            return false;
        }
        if (allowHeaderAuth && (isAdmin() || hasCourseHeader("X-Manageable-Course-Ids", courseId))) {
            return true;
        }
        return bounded(() -> provider.canManageCourse(courseId, userId), false, courseId, userId, "canManageCourse");
    }

    @Override
    public boolean canViewCourse(long courseId, long userId) {
        if (courseId <= 0 || userId <= 0) {
            return false;
        }
        if (allowHeaderAuth
                && (isAdmin()
                || hasCourseHeader("X-Course-Ids", courseId)
                || hasCourseHeader("X-Manageable-Course-Ids", courseId))) {
            return true;
        }
        return bounded(() -> provider.canViewCourse(courseId, userId), false, courseId, userId, "canViewCourse");
    }

    @Override
    public boolean isCourseMember(long courseId, long userId) {
        return canViewCourse(courseId, userId);
    }

    @Override
    public boolean canManageCourseGrade(long courseId, long userId) {
        return canManageCourse(courseId, userId);
    }

    @Override
    public List<Long> listCourseStudentIds(long courseId) {
        if (courseId <= 0) {
            return List.of();
        }
        if (allowHeaderAuth) {
            HttpServletRequest request = currentRequest();
            if (request != null) {
                String roster = request.getHeader("X-Course-Student-Ids");
                if (roster != null && !roster.isBlank()) {
                    List<Long> headerRoster = parseCourseScopedIds(roster, courseId);
                    if (!headerRoster.isEmpty() || roster.contains(":")) {
                        return headerRoster;
                    }
                }
            }
        }
        return boundedList(() -> provider.listActiveStudentIds(courseId), courseId, "listActiveStudentIds");
    }

    @Override
    public List<Long> listCourseTeacherIds(long courseId) {
        if (courseId <= 0) {
            return List.of();
        }
        if (allowHeaderAuth) {
            HttpServletRequest request = currentRequest();
            if (request != null) {
                String teacherRoster = request.getHeader("X-Course-Teacher-Ids");
                if (teacherRoster != null && !teacherRoster.isBlank()) {
                    return parseCourseScopedIds(teacherRoster, courseId);
                }
            }
        }
        return boundedList(() -> provider.listActiveTeacherIds(courseId), courseId, "listActiveTeacherIds");
    }

    private boolean bounded(
            java.util.function.BooleanSupplier call,
            boolean fallback,
            long courseId,
            long userId,
            String operation
    ) {
        Future<Boolean> future;
        try {
            future = executor.submit(call::getAsBoolean);
        } catch (RejectedExecutionException exception) {
            log.warn("CoursePermissionClient {} saturated courseId={} userId={}; denying",
                    operation, courseId, userId);
            return fallback;
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            log.warn("CoursePermissionClient {} timed out after {}ms courseId={} userId={}; denying",
                    operation, timeout.toMillis(), courseId, userId);
            return fallback;
        } catch (ExecutionException exception) {
            future.cancel(true);
            log.warn("CoursePermissionClient {} failed downstream courseId={} userId={}; denying: {}",
                    operation, courseId, userId, rootMessage(exception));
            return fallback;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return fallback;
        }
    }

    private List<Long> boundedList(
            java.util.function.Supplier<List<Long>> call,
            long courseId,
            String operation
    ) {
        Future<List<Long>> future;
        try {
            future = executor.submit(call::get);
        } catch (RejectedExecutionException exception) {
            log.warn("CoursePermissionClient {} saturated courseId={}; returning empty roster", operation, courseId);
            return List.of();
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            log.warn("CoursePermissionClient {} timed out after {}ms courseId={}; returning empty roster",
                    operation, timeout.toMillis(), courseId);
            return List.of();
        } catch (ExecutionException exception) {
            future.cancel(true);
            log.warn("CoursePermissionClient {} failed downstream courseId={}; returning empty roster: {}",
                    operation, courseId, rootMessage(exception));
            return List.of();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static String rootMessage(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null ? exception.toString() : cause.toString();
    }

    private boolean isAdmin() {
        HttpServletRequest request = currentRequest();
        return request != null && "ADMIN".equals(request.getHeader("X-User-Role"));
    }

    private boolean hasCourseHeader(String headerName, long courseId) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String courseIds = request.getHeader(headerName);
        if (courseIds == null || courseIds.isBlank()) {
            return false;
        }
        return Arrays.stream(courseIds.split(","))
                .map(String::trim)
                .anyMatch(value -> "*".equals(value) || Long.toString(courseId).equals(value));
    }

    private List<Long> parseCourseScopedIds(String roster, long courseId) {
        return Arrays.stream(roster.split(";"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> value.startsWith(courseId + ":"))
                .findFirst()
                .map(value -> parseStudentIds(value.substring(value.indexOf(':') + 1)))
                .orElseGet(() -> roster.contains(":") ? List.of() : parseStudentIds(roster));
    }

    private List<Long> parseStudentIds(String value) {
        List<Long> studentIds = new java.util.ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                studentIds.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return List.copyOf(new java.util.LinkedHashSet<>(studentIds));
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest();
    }

    private static Duration requirePositiveTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.course.timeout-ms must be a positive value, got " + timeoutMs
            );
        }
        return Duration.ofMillis(timeoutMs);
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("course permission timeout must be a positive duration");
        }
        return timeout;
    }

    private static int requirePositivePoolSize(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.course.max-pool-size must be positive, got " + maxPoolSize
            );
        }
        return maxPoolSize;
    }

    private static int requireNonNegativeQueueCapacity(int queueCapacity) {
        if (queueCapacity < 0) {
            throw new IllegalArgumentException(
                    "onlinejudge.integration.course.queue-capacity must not be negative, got " + queueCapacity
            );
        }
        return queueCapacity;
    }
}
