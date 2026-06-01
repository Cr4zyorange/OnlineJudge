package com.onlinejudge.lrn.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.lrn.domain.LearningProgress;
import com.onlinejudge.lrn.repository.JdbcLearningProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LearningProgressService {
    private static final Set<String> SUPPORTED_SOURCE_MODULES = Set.of("CRS", "LAB", "HWK");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcLearningProgressRepository progressRepository;

    public LearningProgressService(JdbcLearningProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    public LearningProgressOverview listProgress(long userId, Long courseId) {
        if (courseId != null) {
            requirePositive(courseId, "课程ID不合法");
            requireCourseMember(userId, courseId);
        }
        List<LearningProgress> records = progressRepository.findByUser(userId, courseId);
        List<LearningCourseProgress> courses = toCourseProgress(records);
        return new LearningProgressOverview(courses, courses.size());
    }

    @Transactional
    public LearningProgressItem saveProgress(long userId, LearningProgressSaveRequest request) {
        LearningProgressSaveCommand command = normalizeRequest(request);
        requireCourseMember(userId, command.courseId());
        if (command.chapterId() != null && !progressRepository.chapterBelongsToCourse(command.chapterId(), command.courseId())) {
            throw badRequest("章节不属于当前课程");
        }
        LearningProgress saved = progressRepository.save(userId, command, statusFor(command.progressPercent()));
        return toItem(saved);
    }

    private LearningProgressSaveCommand normalizeRequest(LearningProgressSaveRequest request) {
        if (request == null) {
            throw badRequest("学习进度请求不能为空");
        }
        long courseId = requirePositive(request.courseId(), "课程ID不合法");
        Long chapterId = request.chapterId();
        if (chapterId != null) {
            requirePositive(chapterId, "章节ID不合法");
        }
        long sourceId = requirePositive(request.sourceId(), "来源ID不合法");
        int progressPercent = request.progressPercent() == null ? -1 : request.progressPercent();
        if (progressPercent < 0 || progressPercent > 100) {
            throw badRequest("学习进度百分比必须在0到100之间");
        }
        String sourceModule = normalizeSourceModule(request.sourceModule());
        String lastPosition = normalizeLastPosition(request.lastPosition());
        return new LearningProgressSaveCommand(courseId, chapterId, sourceModule, sourceId, progressPercent, lastPosition);
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw badRequest(message);
        }
        return value;
    }

    private void requireCourseMember(long userId, long courseId) {
        if (!progressRepository.isActiveCourseMember(userId, courseId)) {
            throw new AccessDeniedException("无权访问该课程学习进度");
        }
    }

    private String normalizeSourceModule(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("来源模块不合法");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SOURCE_MODULES.contains(normalized)) {
            throw badRequest("来源模块不合法");
        }
        return normalized;
    }

    private String normalizeLastPosition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw badRequest("断点位置长度不能超过500个字符");
        }
        return normalized;
    }

    private ApiException badRequest(String message) {
        return new ApiException("LRN-400-02", message, HttpStatus.BAD_REQUEST);
    }

    private List<LearningCourseProgress> toCourseProgress(List<LearningProgress> records) {
        Map<Long, List<LearningProgress>> byCourse = new LinkedHashMap<>();
        for (LearningProgress record : records) {
            byCourse.computeIfAbsent(record.courseId(), ignored -> new ArrayList<>()).add(record);
        }
        return byCourse.values().stream()
                .map(this::toCourse)
                .toList();
    }

    private LearningCourseProgress toCourse(List<LearningProgress> records) {
        LearningProgress first = records.get(0);
        List<LearningProgressItem> items = records.stream().map(this::toItem).toList();
        LearningProgressItem continueLearning = chooseContinueItem(items);
        int progressPercent = averageProgress(records);
        return new LearningCourseProgress(
                first.courseId(),
                first.courseName(),
                progressPercent,
                statusFor(progressPercent),
                continueLearning == null ? null : continueLearning.lastPosition(),
                continueLearning == null ? null : continueLearning.continueUrl(),
                continueLearning == null ? null : continueLearning.updatedAt(),
                continueLearning,
                toChapterProgress(records)
        );
    }

    private List<LearningChapterProgress> toChapterProgress(List<LearningProgress> records) {
        Map<Long, List<LearningProgress>> byChapter = new LinkedHashMap<>();
        for (LearningProgress record : records) {
            if (record.chapterId() != null) {
                byChapter.computeIfAbsent(record.chapterId(), ignored -> new ArrayList<>()).add(record);
            }
        }
        return byChapter.values().stream()
                .map(this::toChapter)
                .toList();
    }

    private LearningChapterProgress toChapter(List<LearningProgress> records) {
        LearningProgress first = records.get(0);
        List<LearningProgressItem> items = records.stream().map(this::toItem).toList();
        LearningProgressItem continueLearning = chooseContinueItem(items);
        int progressPercent = averageProgress(records);
        return new LearningChapterProgress(
                first.chapterId(),
                first.chapterName() == null || first.chapterName().isBlank() ? "章节 " + first.chapterId() : first.chapterName(),
                progressPercent,
                statusFor(progressPercent),
                continueLearning == null ? null : continueLearning.lastPosition(),
                continueLearning == null ? null : continueLearning.continueUrl(),
                continueLearning == null ? null : continueLearning.updatedAt(),
                items
        );
    }

    private LearningProgressItem chooseContinueItem(List<LearningProgressItem> items) {
        return items.stream()
                .filter(item -> !"COMPLETED".equals(item.status()))
                .max(Comparator.comparing(LearningProgressItem::updatedAt))
                .orElseGet(() -> items.stream()
                        .max(Comparator.comparing(LearningProgressItem::updatedAt))
                        .orElse(null));
    }

    private int averageProgress(List<LearningProgress> records) {
        return (int) Math.round(records.stream()
                .mapToInt(LearningProgress::progressPercent)
                .average()
                .orElse(0));
    }

    private LearningProgressItem toItem(LearningProgress progress) {
        return new LearningProgressItem(
                progress.id(),
                progress.courseId(),
                progress.courseName(),
                progress.chapterId(),
                progress.chapterName(),
                progress.sourceModule(),
                progress.sourceId(),
                progress.progressPercent(),
                progress.lastPosition(),
                statusFor(progress.progressPercent()),
                continueUrl(progress),
                formatTime(progress.updatedAt())
        );
    }

    private String continueUrl(LearningProgress progress) {
        return switch (progress.sourceModule()) {
            case "LAB" -> "/courses/" + progress.courseId() + "/labs/" + progress.sourceId();
            case "HWK" -> "/courses/" + progress.courseId() + "/homeworks/" + progress.sourceId();
            default -> progress.chapterId() == null
                    ? "/courses/" + progress.courseId()
                    : "/courses/" + progress.courseId() + "?chapterId=" + progress.chapterId();
        };
    }

    private String statusFor(int progressPercent) {
        if (progressPercent >= 100) {
            return "COMPLETED";
        }
        if (progressPercent <= 0) {
            return "NOT_STARTED";
        }
        return "IN_PROGRESS";
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }
}
