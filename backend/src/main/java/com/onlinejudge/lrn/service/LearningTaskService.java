package com.onlinejudge.lrn.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.lrn.domain.LearningTask;
import com.onlinejudge.lrn.repository.JdbcLearningTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LearningTaskService {
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of("RESOURCE", "EXPERIMENT", "HOMEWORK");
    private static final Set<String> SUPPORTED_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "OVERDUE");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcLearningTaskRepository learningTaskRepository;

    public LearningTaskService(JdbcLearningTaskRepository learningTaskRepository) {
        this.learningTaskRepository = learningTaskRepository;
    }

    public LearningTaskPage listTasks(long userId, LearningTaskQuery query) {
        Set<String> taskTypes = parseTaskTypes(query.taskType());
        String requestedStatus = normalizeOptional(query.status());
        if (requestedStatus != null && !SUPPORTED_STATUSES.contains(requestedStatus)) {
            throw new ApiException("LRN-400-01", "任务状态不合法", HttpStatus.BAD_REQUEST);
        }

        int page = normalizePage(query.page());
        int size = normalizeSize(query.size());
        boolean ascending = !"desc".equalsIgnoreCase(query.order());
        String sortBy = normalizeSortBy(query.sortBy());
        LocalDateTime now = LocalDateTime.now();

        List<LearningTaskWithStatus> filtered = deduplicateBySource(learningTaskRepository.findByUserId(userId)).stream()
                .filter(task -> query.courseId() == null || task.courseId() == query.courseId())
                .filter(task -> taskTypes.isEmpty() || taskTypes.contains(normalizeRequired(task.taskType(), "任务类型不合法")))
                .map(task -> new LearningTaskWithStatus(task, effectiveStatus(task, now)))
                .filter(task -> requestedStatus == null || requestedStatus.equals(task.status()))
                .sorted(taskComparator(sortBy, ascending))
                .toList();

        long total = filtered.size();
        int fromIndex = Math.min((page - 1) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<LearningTaskSummary> records = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toSummary)
                .toList();
        return new LearningTaskPage(records, total, page, size);
    }

    private List<LearningTask> deduplicateBySource(List<LearningTask> tasks) {
        Map<String, LearningTask> deduplicated = new LinkedHashMap<>();
        for (LearningTask task : tasks) {
            String key = task.sourceModule() + ":" + task.courseId() + ":" + task.sourceId() + ":" + task.taskType();
            deduplicated.putIfAbsent(key, task);
        }
        return List.copyOf(deduplicated.values());
    }

    private Set<String> parseTaskTypes(String rawTaskTypes) {
        if (rawTaskTypes == null || rawTaskTypes.isBlank()) {
            return Set.of();
        }
        Set<String> taskTypes = List.of(rawTaskTypes.split(",")).stream()
                .map(this::normalizeOptional)
                .filter(value -> value != null)
                .collect(Collectors.toUnmodifiableSet());
        if (!SUPPORTED_TASK_TYPES.containsAll(taskTypes)) {
            throw new ApiException("LRN-400-01", "任务类型不合法", HttpStatus.BAD_REQUEST);
        }
        return taskTypes;
    }

    private String effectiveStatus(LearningTask task, LocalDateTime now) {
        String status = normalizeRequired(task.status(), "任务状态不合法");
        if (!SUPPORTED_STATUSES.contains(status)) {
            throw new ApiException("LRN-400-01", "任务状态不合法", HttpStatus.BAD_REQUEST);
        }
        if (!"COMPLETED".equals(status) && task.deadline() != null && task.deadline().isBefore(now)) {
            return "OVERDUE";
        }
        return status;
    }

    private Comparator<LearningTaskWithStatus> taskComparator(String sortBy, boolean ascending) {
        return (left, right) -> {
            int compared = switch (sortBy) {
                case "createdAt" -> compareNullable(left.task().createdAt(), right.task().createdAt(), ascending);
                default -> compareNullable(left.task().deadline(), right.task().deadline(), ascending);
            };
            if (compared != 0) {
                return compared;
            }
            return Long.compare(left.task().id(), right.task().id());
        };
    }

    private int compareNullable(LocalDateTime left, LocalDateTime right, boolean ascending) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return ascending ? left.compareTo(right) : right.compareTo(left);
    }

    private LearningTaskSummary toSummary(LearningTaskWithStatus taskWithStatus) {
        LearningTask task = taskWithStatus.task();
        return new LearningTaskSummary(
                task.id(),
                normalizeRequired(task.taskType(), "任务类型不合法"),
                task.title(),
                task.courseId(),
                task.courseName(),
                task.deadline() == null ? null : task.deadline().format(RESPONSE_TIME_FORMAT),
                Math.max(0, Math.min(100, task.progress())),
                taskWithStatus.status(),
                task.actionUrl()
        );
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.max(1, Math.min(size, 100));
    }

    private String normalizeSortBy(String sortBy) {
        String normalized = sortBy == null || sortBy.isBlank() ? "deadline" : sortBy.trim();
        if (!"deadline".equals(normalized) && !"createdAt".equals(normalized)) {
            throw new ApiException("LRN-400-01", "排序字段不合法", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ApiException("LRN-400-01", message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private record LearningTaskWithStatus(LearningTask task, String status) {
    }
}
