package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.HomeworkStatisticsRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-HWK-15 orchestration.  The repository retains all roster and submission aggregation in SQL. */
@Service
public class HomeworkStatisticsService {
    private final HomeworkStatisticsRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public HomeworkStatisticsService(HomeworkStatisticsRepository repository) {
        this(repository, Clock.systemUTC());
    }

    HomeworkStatisticsService(HomeworkStatisticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Map<String, Object> statistics(HomeworkService.HomeworkSummary homework, int page, int size) {
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String creator = repository.creatorFor(homework.id());
        HomeworkStatisticsRepository.Aggregate aggregate = repository.aggregate(homework.id(), homework.courseId(), creator,
                homework.totalScore());
        int unsubmittedTotal = repository.unsubmittedTotal(homework.id(), homework.courseId(), creator);
        List<Object> unsubmitted = repository.unsubmittedStudentIds(homework.id(), homework.courseId(), creator, size,
                        ((long) page - 1L) * size)
                .stream().map(HomeworkStatisticsService::numericIfPossible).toList();
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0-59", aggregate.score0To59());
        distribution.put("60-69", aggregate.score60To69());
        distribution.put("70-79", aggregate.score70To79());
        distribution.put("80-89", aggregate.score80To89());
        distribution.put("90-100", aggregate.score90To100());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("homeworkId", homework.id());
        response.put("courseId", numericIfPossible(homework.courseId()));
        response.put("totalStudentCount", aggregate.totalStudentCount());
        response.put("submittedCount", aggregate.submittedCount());
        response.put("unsubmittedCount", unsubmittedTotal);
        response.put("evaluatedCount", aggregate.evaluatedCount());
        response.put("reviewedCount", aggregate.reviewedCount());
        response.put("averageScore", aggregate.averageScore());
        response.put("maxScore", aggregate.maxScore());
        response.put("minScore", aggregate.minScore());
        response.put("unsubmittedPage", page);
        response.put("unsubmittedSize", size);
        response.put("unsubmittedTotal", unsubmittedTotal);
        response.put("unsubmittedStudentIds", unsubmitted);
        response.put("autoEvaluableCount", aggregate.autoEvaluableCount());
        response.put("pendingEvaluationCount", aggregate.pendingEvaluationCount());
        response.put("pendingReviewCount", aggregate.pendingReviewCount());
        response.put("scoredCount", aggregate.scoredCount());
        response.put("scoreDistribution", distribution);
        response.put("generatedAt", Instant.now(clock));
        return response;
    }

    private static Object numericIfPossible(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return value; }
    }
}
