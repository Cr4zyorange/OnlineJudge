package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeProvider;
import com.onlinejudge.integration.grade.SourceGradeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class HomeworkSourceGradeClient implements SourceGradeProvider {
    private final HomeworkRepository homeworkRepository;
    private final HomeworkService homeworkService;

    public HomeworkSourceGradeClient(
            HomeworkRepository homeworkRepository,
            HomeworkService homeworkService
    ) {
        this.homeworkRepository = homeworkRepository;
        this.homeworkService = homeworkService;
    }

    @Override
    public boolean supports(SourceGradeType sourceType) {
        return sourceType == SourceGradeType.HWK;
    }

    @Override
    public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
        Optional<Homework> homework = homeworkRepository.findById(sourceId)
                .filter(item -> !item.deleted())
                .filter(item -> item.courseId() == courseId);
        if (homework.isEmpty()) {
            return Optional.empty();
        }
        if (homework.get().status() != HomeworkStatus.SCORE_PUBLISHED
                && homework.get().status() != HomeworkStatus.ARCHIVED) {
            return Optional.of(List.of());
        }
        BigDecimal fullScore = BigDecimal.valueOf(homework.get().totalScore());
        return Optional.of(homeworkService.finalSubmissions(sourceId).stream()
                .map(submission -> toSourceGrade(homework.get(), submission, fullScore))
                .toList());
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
