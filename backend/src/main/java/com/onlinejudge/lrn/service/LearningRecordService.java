package com.onlinejudge.lrn.service;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.security.AccessDeniedException;
import com.onlinejudge.lrn.domain.LearningRecord;
import com.onlinejudge.lrn.repository.JdbcLearningRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class LearningRecordService {
    private static final Set<String> SUPPORTED_SOURCE_MODULES = Set.of("CRS", "LAB", "HWK");
    private static final Set<String> SUPPORTED_ACTION_TYPES = Set.of("ACCESS", "DOWNLOAD", "STUDY", "SUBMIT", "COMPLETE");
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_DURATION_SECONDS = 24 * 60 * 60;
    private static final int RATE_LIMIT_PER_MINUTE = 10;

    private final JdbcLearningRecordRepository recordRepository;
    private final LearningRecordAsyncWriter asyncWriter;
    private final ConcurrentMap<String, Deque<LocalDateTime>> receivedReports = new ConcurrentHashMap<>();

    public LearningRecordService(JdbcLearningRecordRepository recordRepository, LearningRecordAsyncWriter asyncWriter) {
        this.recordRepository = recordRepository;
        this.asyncWriter = asyncWriter;
    }

    public LearningStatisticsOverview getStatistics(long userId, Long courseId) {
        if (courseId != null) {
            requirePositive(courseId, "课程ID不合法");
            requireCourseMember(userId, courseId);
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime since = startDate.atStartOfDay();
        List<LearningRecord> records = recordRepository.findByUserSince(userId, courseId, since);
        return new LearningStatisticsOverview(
                toSummary(records),
                toTrends(records, startDate, today),
                records.stream()
                        .sorted(Comparator.comparing(LearningRecord::startedAt).reversed()
                                .thenComparing(Comparator.comparingLong(LearningRecord::id).reversed()))
                        .limit(10)
                        .map(this::toItem)
                        .toList()
        );
    }

    public LearningRecordItem reportRecord(long userId, LearningRecordRequest request) {
        LearningRecordCommand command = normalizeRequest(request);
        requireCourseMember(userId, command.courseId());
        LocalDateTime receivedAt = reserveRateLimitSlot(userId, command);
        if (receivedAt == null) {
            throw new ApiException("LRN-429-03", "学习行为上报过于频繁", HttpStatus.TOO_MANY_REQUESTS);
        }
        asyncWriter.saveAsync(userId, command)
                .whenComplete((ignored, error) -> releaseRateLimitSlot(userId, command, receivedAt));
        return acceptedItem(command);
    }

    private LearningRecordCommand normalizeRequest(LearningRecordRequest request) {
        if (request == null) {
            throw badRequest("学习行为记录不能为空");
        }
        long courseId = requirePositive(request.courseId(), "课程ID不合法");
        long sourceId = requirePositive(request.sourceId(), "来源ID不合法");
        String sourceModule = normalizeEnum(request.sourceModule(), SUPPORTED_SOURCE_MODULES, "来源模块不合法");
        String actionType = normalizeEnum(request.actionType(), SUPPORTED_ACTION_TYPES, "行为类型不合法");
        LocalDateTime endedAt = request.endedAt() == null ? LocalDateTime.now() : request.endedAt();
        int durationSeconds;
        LocalDateTime startedAt;
        if (request.startedAt() == null) {
            durationSeconds = request.durationSeconds() == null ? 0 : request.durationSeconds();
            startedAt = endedAt.minusSeconds(Math.max(durationSeconds, 0));
        } else {
            startedAt = request.startedAt();
            long calculatedSeconds = Math.max(0, Duration.between(startedAt, endedAt).getSeconds());
            durationSeconds = request.durationSeconds() == null ? (int) calculatedSeconds : request.durationSeconds();
        }
        if (startedAt.isAfter(endedAt)) {
            throw badRequest("开始时间不能晚于结束时间");
        }
        if (durationSeconds < 0 || durationSeconds > MAX_DURATION_SECONDS) {
            throw badRequest("学习时长必须在0到86400秒之间");
        }
        return new LearningRecordCommand(courseId, sourceModule, sourceId, actionType, durationSeconds, startedAt, endedAt);
    }

    private LearningStatisticsSummary toSummary(List<LearningRecord> records) {
        return new LearningStatisticsSummary(
                records.stream().mapToInt(LearningRecord::durationSeconds).sum(),
                (int) records.stream().filter(this::isAccessAction).count(),
                (int) records.stream().filter(record -> "COMPLETE".equals(record.actionType())).count(),
                (int) records.stream().filter(record -> "SUBMIT".equals(record.actionType())).count(),
                records.size()
        );
    }

    private List<LearningTrendPoint> toTrends(List<LearningRecord> records, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<LearningRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(record -> record.startedAt().toLocalDate()));
        List<LearningTrendPoint> trends = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate trendDate = date;
            List<LearningRecord> dayRecords = byDate.getOrDefault(trendDate, List.of());
            trends.add(new LearningTrendPoint(
                    trendDate.toString(),
                    dayRecords.stream().mapToInt(LearningRecord::durationSeconds).sum(),
                    (int) dayRecords.stream().filter(this::isAccessAction).count(),
                    (int) dayRecords.stream().filter(record -> "COMPLETE".equals(record.actionType())).count()
            ));
        }
        return trends;
    }

    private boolean isAccessAction(LearningRecord record) {
        return "ACCESS".equals(record.actionType()) || "DOWNLOAD".equals(record.actionType());
    }

    private LearningRecordItem toItem(LearningRecord record) {
        return new LearningRecordItem(
                record.id(),
                record.courseId(),
                record.courseName(),
                record.sourceModule(),
                record.sourceId(),
                record.actionType(),
                record.durationSeconds(),
                formatTime(record.startedAt()),
                formatTime(record.endedAt())
        );
    }

    private LearningRecordItem acceptedItem(LearningRecordCommand command) {
        return new LearningRecordItem(
                0L,
                command.courseId(),
                "",
                command.sourceModule(),
                command.sourceId(),
                command.actionType(),
                command.durationSeconds(),
                formatTime(command.startedAt()),
                formatTime(command.endedAt())
        );
    }

    private LocalDateTime reserveRateLimitSlot(long userId, LearningRecordCommand command) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusMinutes(1);
        String key = rateLimitKey(userId, command);
        Deque<LocalDateTime> received = receivedReports.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (received) {
            while (!received.isEmpty() && received.peekFirst().isBefore(since)) {
                received.removeFirst();
            }
            int persistedCount = recordRepository.countRecentReports(userId, command, since);
            if (persistedCount + received.size() >= RATE_LIMIT_PER_MINUTE) {
                return null;
            }
            received.addLast(now);
            return now;
        }
    }

    private void releaseRateLimitSlot(long userId, LearningRecordCommand command, LocalDateTime receivedAt) {
        String key = rateLimitKey(userId, command);
        Deque<LocalDateTime> received = receivedReports.get(key);
        if (received == null) {
            return;
        }
        synchronized (received) {
            received.remove(receivedAt);
            if (received.isEmpty()) {
                receivedReports.remove(key, received);
            }
        }
    }

    private String rateLimitKey(long userId, LearningRecordCommand command) {
        return userId + ":" + command.courseId() + ":" + command.sourceModule() + ":" + command.sourceId();
    }

    private void requireCourseMember(long userId, long courseId) {
        if (!recordRepository.isActiveCourseMember(userId, courseId)) {
            throw new AccessDeniedException("无权访问该课程学习行为");
        }
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw badRequest(message);
        }
        return value;
    }

    private String normalizeEnum(String value, Set<String> supportedValues, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!supportedValues.contains(normalized)) {
            throw badRequest(message);
        }
        return normalized;
    }

    private ApiException badRequest(String message) {
        return new ApiException("LRN-400-03", message, HttpStatus.BAD_REQUEST);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(RESPONSE_TIME_FORMAT);
    }
}
