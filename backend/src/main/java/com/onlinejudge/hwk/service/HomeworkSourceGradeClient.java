package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.integration.grade.DemoSourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Primary
@Component
public class HomeworkSourceGradeClient implements SourceGradeClient {
    private final HomeworkRepository homeworkRepository;
    private final HomeworkService homeworkService;
    private final DemoSourceGradeClient fallbackSourceGradeClient;

    public HomeworkSourceGradeClient(
            HomeworkRepository homeworkRepository,
            HomeworkService homeworkService,
            DemoSourceGradeClient fallbackSourceGradeClient
    ) {
        this.homeworkRepository = homeworkRepository;
        this.homeworkService = homeworkService;
        this.fallbackSourceGradeClient = fallbackSourceGradeClient;
    }

    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        if (sourceType != SourceGradeType.HWK) {
            return fallbackSourceGradeClient.findSourceGrades(courseId, sourceType, sourceId);
        }
        Optional<Homework> homework = homeworkRepository.findById(sourceId)
                .filter(item -> !item.deleted())
                .filter(item -> item.courseId() == courseId);
        if (homework.isEmpty()) {
            return List.of();
        }
        if (homework.get().status() != HomeworkStatus.SCORE_PUBLISHED
                && homework.get().status() != HomeworkStatus.ARCHIVED) {
            return List.of();
        }
        BigDecimal fullScore = BigDecimal.valueOf(homework.get().totalScore());
        return homeworkService.finalSubmissions(sourceId).stream()
                .map(submission -> toSourceGrade(homework.get(), submission, fullScore))
                .toList();
    }

    private SourceGradeDTO toSourceGrade(Homework homework, HomeworkSubmission submission, BigDecimal fullScore) {
        Optional<BigDecimal> score = homeworkService.effectiveScore(submission);
        return new SourceGradeDTO(
                homework.courseId(),
                SourceGradeType.HWK,
                homework.id(),
                submission.studentId(),
                score.orElse(null),
                fullScore,
                score.isPresent() ? "SCORED" : "UNGRADED",
                submission.updatedAt()
        );
    }
}
