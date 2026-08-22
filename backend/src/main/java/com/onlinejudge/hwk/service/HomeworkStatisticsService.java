package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatisticsAggregate;
import com.onlinejudge.hwk.domain.HomeworkStatisticsRepository;
import com.onlinejudge.hwk.domain.HomeworkStatisticsView;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class HomeworkStatisticsService {
    private final HomeworkRepository homeworkRepository;
    private final HomeworkStatisticsRepository statisticsRepository;
    private final CoursePermissionClient coursePermissionClient;

    public HomeworkStatisticsService(
            HomeworkRepository homeworkRepository,
            HomeworkStatisticsRepository statisticsRepository,
            CoursePermissionClient coursePermissionClient
    ) {
        this.homeworkRepository = homeworkRepository;
        this.statisticsRepository = statisticsRepository;
        this.coursePermissionClient = coursePermissionClient;
    }

    @Transactional(readOnly = true)
    public HomeworkStatisticsView statistics(long homeworkId, long managerId, int page, int size) {
        Homework homework = homeworkRepository.findById(homeworkId)
                .filter(item -> !item.deleted())
                .orElseThrow(() -> new HomeworkApiException(
                        "HWK_4001",
                        "homework not found",
                        HttpStatus.NOT_FOUND
                ));
        if (!coursePermissionClient.canManageCourse(homework.courseId(), managerId)) {
            throw new HomeworkApiException(
                    "HWK_4031",
                    "course management permission denied",
                    HttpStatus.FORBIDDEN
            );
        }

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<Long> activeStudentIds = coursePermissionClient.listCourseStudentIds(homework.courseId()).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        HomeworkStatisticsAggregate aggregate = statisticsRepository.aggregate(
                homework.id(),
                homework.totalScore(),
                activeStudentIds
        );
        Set<Long> submittedStudentIds = new HashSet<>(
                statisticsRepository.findSubmittedStudentIds(homework.id(), activeStudentIds)
        );
        List<Long> unsubmittedStudentIds = activeStudentIds.stream()
                .filter(studentId -> !submittedStudentIds.contains(studentId))
                .toList();
        long offset = ((long) normalizedPage - 1L) * normalizedSize;
        int fromIndex = offset >= unsubmittedStudentIds.size() ? unsubmittedStudentIds.size() : (int) offset;
        int toIndex = Math.min(fromIndex + normalizedSize, unsubmittedStudentIds.size());

        return new HomeworkStatisticsView(
                homework.id(),
                homework.courseId(),
                activeStudentIds.size(),
                aggregate.submittedCount(),
                unsubmittedStudentIds.size(),
                aggregate.autoEvaluableCount(),
                aggregate.evaluatedCount(),
                aggregate.pendingEvaluationCount(),
                aggregate.pendingReviewCount(),
                aggregate.reviewedCount(),
                aggregate.scoredCount(),
                aggregate.averageScore(),
                aggregate.maxScore(),
                aggregate.minScore(),
                normalizedPage,
                normalizedSize,
                unsubmittedStudentIds.size(),
                unsubmittedStudentIds.subList(fromIndex, toIndex),
                scoreDistribution(aggregate),
                LocalDateTime.now()
        );
    }

    private Map<String, Integer> scoreDistribution(HomeworkStatisticsAggregate aggregate) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0-59", aggregate.score0To59());
        distribution.put("60-69", aggregate.score60To69());
        distribution.put("70-79", aggregate.score70To79());
        distribution.put("80-89", aggregate.score80To89());
        distribution.put("90-100", aggregate.score90To100());
        return distribution;
    }
}
