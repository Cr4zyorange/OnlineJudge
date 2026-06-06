package com.onlinejudge.lab.service;

import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeProvider;
import com.onlinejudge.integration.grade.SourceGradeType;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabScore;
import com.onlinejudge.lab.domain.LabScoreRepository;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class LabSourceGradeService implements SourceGradeProvider {
    private final LabExperimentRepository labExperimentRepository;
    private final LabSubmissionRepository labSubmissionRepository;
    private final LabScoreRepository labScoreRepository;

    public LabSourceGradeService(
            LabExperimentRepository labExperimentRepository,
            LabSubmissionRepository labSubmissionRepository,
            LabScoreRepository labScoreRepository
    ) {
        this.labExperimentRepository = labExperimentRepository;
        this.labSubmissionRepository = labSubmissionRepository;
        this.labScoreRepository = labScoreRepository;
    }

    @Override
    public boolean supports(SourceGradeType sourceType) {
        return sourceType == SourceGradeType.LAB;
    }

    @Override
    public Optional<List<SourceGradeDTO>> findSourceGrades(long courseId, long sourceId) {
        Optional<LabExperiment> experiment = labExperimentRepository.findById(sourceId)
                .filter(item -> !item.deleted())
                .filter(item -> item.courseId() == courseId);
        if (experiment.isEmpty()) {
            return Optional.empty();
        }
        if (!isGradeVisibleToGrd(experiment.get())) {
            return Optional.of(List.of());
        }

        BigDecimal fullScore = BigDecimal.valueOf(experiment.get().maxScore());
        return Optional.of(labSubmissionRepository.findByLabId(sourceId).stream()
                .filter(submission -> submission.isFinal() && !submission.deleted())
                .map(submission -> toSourceGrade(experiment.get(), submission, fullScore))
                .toList());
    }

    private boolean isGradeVisibleToGrd(LabExperiment experiment) {
        return experiment.status() == LabExperimentStatus.SCORE_PUBLISHED
                || experiment.status() == LabExperimentStatus.ARCHIVED;
    }

    private SourceGradeDTO toSourceGrade(LabExperiment experiment, LabSubmission submission, BigDecimal fullScore) {
        Optional<LabScore> score = labScoreRepository.findBySubmissionId(submission.id());
        return new SourceGradeDTO(
                experiment.courseId(),
                SourceGradeType.LAB,
                experiment.id(),
                submission.studentId(),
                score.map(item -> BigDecimal.valueOf(item.finalScore())).orElse(null),
                fullScore,
                score.isPresent() ? "SCORED" : "UNGRADED",
                score.map(LabScore::updatedAt).orElse(submission.updatedAt())
        );
    }
}
