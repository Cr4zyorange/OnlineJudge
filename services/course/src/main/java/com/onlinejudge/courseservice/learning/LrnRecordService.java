package com.onlinejudge.courseservice.learning;

import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/** Course-owned learning record facts (LRN folded into Course, #355). */
@Service
public class LrnRecordService {
    private static final Set<String> SUPPORTED_ACTIONS = Set.of("ACCESS", "DOWNLOAD", "STUDY", "SUBMIT", "COMPLETE");
    private static final Set<String> SUPPORTED_MODULES = Set.of("CRS", "LAB", "HWK");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LrnRecordRepository records;

    public LrnRecordService(LrnRecordRepository records) { this.records = records; }

    public LearningStatisticsOverview statistics(long userId, Long courseId) {
        LrnRecordRepository.Summary summary = records.summary(userId, courseId);
        List<LearningTrendPoint> trends = records.trends(userId, courseId, 7).stream()
                .map(row -> new LearningTrendPoint(row.date().format(DAY_FORMAT), row.durationSeconds(),
                        row.resourceAccessCount(), row.completedTaskCount()))
                .toList();
        List<LearningRecordItem> recent = records.recent(userId, courseId, 10).stream().map(this::item).toList();
        return new LearningStatisticsOverview(
                new LearningStatisticsSummary(summary.totalDurationSeconds(), summary.resourceAccessCount(),
                        summary.completedTaskCount(), summary.submittedTaskCount(), summary.totalRecordCount()),
                trends, recent);
    }

    @Transactional
    public LearningRecordItem report(long userId, LearningRecordRequest request) {
        if (request == null || request.courseId() == null || request.sourceModule() == null || request.sourceId() == null
                || request.actionType() == null) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "LEARNING_RECORD_INVALID", "学习记录参数不合法", false);
        }
        String module = request.sourceModule().trim().toUpperCase(java.util.Locale.ROOT);
        String action = request.actionType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!SUPPORTED_MODULES.contains(module)) throw new CourseException(HttpStatus.BAD_REQUEST, "LEARNING_RECORD_INVALID", "来源模块不合法", false);
        if (!SUPPORTED_ACTIONS.contains(action)) throw new CourseException(HttpStatus.BAD_REQUEST, "LEARNING_RECORD_INVALID", "行为类型不合法", false);
        LocalDateTime startedAt = request.startedAt() == null ? LocalDateTime.now() : request.startedAt();
        LocalDateTime endedAt = request.endedAt() == null ? startedAt : request.endedAt();
        if (endedAt.isBefore(startedAt)) throw new CourseException(HttpStatus.BAD_REQUEST, "LEARNING_RECORD_INVALID", "结束时间不能早于开始时间", false);
        int duration = request.durationSeconds() == null ? 0 : Math.max(0, request.durationSeconds());
        long id = records.insert(userId, request.courseId(), module, request.sourceId(), action, duration, startedAt, endedAt);
        return item(records.findById(userId, id).orElseThrow());
    }

    private LearningRecordItem item(LrnRecordRepository.RecordRow row) {
        return new LearningRecordItem(row.id(), row.courseId(), row.courseName(), row.sourceModule(), row.sourceId(),
                row.actionType(), row.durationSeconds(), format(row.startedAt()), format(row.endedAt()));
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }

    public record LearningStatisticsOverview(LearningStatisticsSummary summary, List<LearningTrendPoint> trends,
                                             List<LearningRecordItem> recentRecords) { }
    public record LearningStatisticsSummary(long totalDurationSeconds, long resourceAccessCount, long completedTaskCount,
                                            long submittedTaskCount, long totalRecordCount) { }
    public record LearningTrendPoint(String date, long durationSeconds, long resourceAccessCount, long completedTaskCount) { }
    public record LearningRecordItem(long id, long courseId, String courseName, String sourceModule, long sourceId,
                                     String actionType, int durationSeconds, String startedAt, String endedAt) { }
    public record LearningRecordRequest(Long courseId, String sourceModule, Long sourceId, String actionType,
                                        Integer durationSeconds, LocalDateTime startedAt, LocalDateTime endedAt) { }
}
