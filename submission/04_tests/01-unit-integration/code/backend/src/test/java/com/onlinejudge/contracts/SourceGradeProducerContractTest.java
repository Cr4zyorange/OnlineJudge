package com.onlinejudge.contracts;

import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.service.HomeworkService;
import com.onlinejudge.hwk.service.HomeworkSourceGradeClient;
import com.onlinejudge.integration.grade.SourceGradeDTO;
import com.onlinejudge.integration.grade.SourceGradeType;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentRepository;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabScore;
import com.onlinejudge.lab.domain.LabScoreRepository;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import com.onlinejudge.lab.service.LabSourceGradeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #310 C-06 来源成绩生产者契约（可独立运行）：LAB/HWK 以冻结字段输出来源成绩。
 */
class SourceGradeProducerContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 10, 0);

    @Test
    void labProviderEmitsScoredDtoWithFrozenFields() {
        LabExperiment experiment = labExperiment(LabExperimentStatus.SCORE_PUBLISHED);
        LabSubmission submission = labSubmission();
        LabScore score = new LabScore(1L, 701L, null, 501L, 80, null, 5, 85, "good",
                NOW.minusHours(1), NOW.minusHours(1));

        LabExperimentRepository experimentRepository = mock(LabExperimentRepository.class);
        LabSubmissionRepository submissionRepository = mock(LabSubmissionRepository.class);
        LabScoreRepository scoreRepository = mock(LabScoreRepository.class);
        when(experimentRepository.findById(301L)).thenReturn(Optional.of(experiment));
        when(submissionRepository.findByLabId(301L)).thenReturn(List.of(submission));
        when(scoreRepository.findBySubmissionId(701L)).thenReturn(Optional.of(score));

        LabSourceGradeService provider = new LabSourceGradeService(
                experimentRepository, submissionRepository, scoreRepository
        );

        List<SourceGradeDTO> grades = provider.findSourceGrades(101L, 301L).orElseThrow();
        assertThat(grades).hasSize(1);
        SourceGradeDTO dto = grades.get(0);
        assertThat(dto.courseId()).isEqualTo(101L);
        assertThat(dto.sourceType()).isEqualTo(SourceGradeType.LAB);
        assertThat(dto.sourceId()).isEqualTo(301L);
        assertThat(dto.studentId()).isEqualTo(601L);
        assertThat(dto.score()).isEqualByComparingTo("85");
        assertThat(dto.fullScore()).isEqualByComparingTo("100");
        assertThat(dto.status()).isEqualTo("SCORED");
        assertThat(dto.updatedAt()).isEqualTo(score.updatedAt());
    }

    @Test
    void labProviderReturnsEmptyWhenGradesAreNotVisibleToGrd() {
        LabExperimentRepository experimentRepository = mock(LabExperimentRepository.class);
        when(experimentRepository.findById(301L))
                .thenReturn(Optional.of(labExperiment(LabExperimentStatus.PUBLISHED)));

        LabSourceGradeService provider = new LabSourceGradeService(
                experimentRepository, mock(LabSubmissionRepository.class), mock(LabScoreRepository.class)
        );

        assertThat(provider.findSourceGrades(101L, 301L)).contains(List.of());
    }

    @Test
    void labProviderReturnsMissingForUnknownOrCrossCourseExperiment() {
        LabExperimentRepository experimentRepository = mock(LabExperimentRepository.class);
        when(experimentRepository.findById(301L)).thenReturn(Optional.empty());

        LabSourceGradeService provider = new LabSourceGradeService(
                experimentRepository, mock(LabSubmissionRepository.class), mock(LabScoreRepository.class)
        );

        assertThat(provider.findSourceGrades(101L, 301L)).isEmpty();
        when(experimentRepository.findById(301L))
                .thenReturn(Optional.of(labExperiment(LabExperimentStatus.SCORE_PUBLISHED)));
        assertThat(provider.findSourceGrades(999L, 301L)).isEmpty();
    }

    @Test
    void labProviderMarksUngradedSubmissionWithoutFabricatingScore() {
        LabExperimentRepository experimentRepository = mock(LabExperimentRepository.class);
        LabSubmissionRepository submissionRepository = mock(LabSubmissionRepository.class);
        LabScoreRepository scoreRepository = mock(LabScoreRepository.class);
        when(experimentRepository.findById(301L))
                .thenReturn(Optional.of(labExperiment(LabExperimentStatus.SCORE_PUBLISHED)));
        when(submissionRepository.findByLabId(301L)).thenReturn(List.of(labSubmission()));
        when(scoreRepository.findBySubmissionId(701L)).thenReturn(Optional.empty());

        LabSourceGradeService provider = new LabSourceGradeService(
                experimentRepository, submissionRepository, scoreRepository
        );

        SourceGradeDTO dto = provider.findSourceGrades(101L, 301L).orElseThrow().get(0);
        assertThat(dto.status()).isEqualTo("UNGRADED");
        assertThat(dto.score()).isNull();
    }

    @Test
    void hwkProviderEmitsScoredDtoOnlyAfterScorePublish() {
        Homework homework = homework(HomeworkStatus.SCORE_PUBLISHED);
        HomeworkSubmission submission = homeworkSubmission();
        HomeworkRepository homeworkRepository = mock(HomeworkRepository.class);
        HomeworkService homeworkService = mock(HomeworkService.class);
        when(homeworkRepository.findById(11L)).thenReturn(Optional.of(homework));
        when(homeworkService.finalSubmissions(11L)).thenReturn(List.of(submission));
        when(homeworkService.effectiveScore(submission)).thenReturn(Optional.of(new BigDecimal("85")));

        HomeworkSourceGradeClient provider = new HomeworkSourceGradeClient(homeworkRepository, homeworkService);

        List<SourceGradeDTO> grades = provider.findSourceGrades(101L, 11L).orElseThrow();
        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).sourceType()).isEqualTo(SourceGradeType.HWK);
        assertThat(grades.get(0).status()).isEqualTo("SCORED");

        when(homeworkRepository.findById(11L)).thenReturn(Optional.of(homework(HomeworkStatus.PUBLISHED)));
        assertThat(provider.findSourceGrades(101L, 11L)).contains(List.of());
    }

    private static LabExperiment labExperiment(LabExperimentStatus status) {
        return new LabExperiment(
                301L, 101L, null, "实验", "描述", status, NOW.plusDays(1), 100,
                List.of(), "python", LabEvaluationMode.DOCKER_IO, true, false,
                1000, 65536, 501L, NOW.minusDays(1), false, NOW.minusDays(2), NOW.minusDays(1), List.of()
        );
    }

    private static LabSubmission labSubmission() {
        return new LabSubmission(
                701L, 301L, 601L, "print(1+2)", null, "python",
                LabSubmitStatus.SUBMITTED, com.onlinejudge.common.evaluation.EvaluationStatus.ACCEPTED,
                null, null, 1, true, NOW.minusHours(2), NOW.minusHours(2), NOW.minusHours(2), false
        );
    }

    private static Homework homework(HomeworkStatus status) {
        return new Homework(
                11L, 101L, null, "作业", "描述", HomeworkType.CODE, status, 100,
                NOW.plusDays(1), true, false, true, null, 501L, NOW, false,
                NOW, NOW, List.of(), List.of(), null
        );
    }

    private static HomeworkSubmission homeworkSubmission() {
        return new HomeworkSubmission(
                31L, 11L, 601L, HomeworkType.CODE, "print(input())", null, null, "python",
                com.onlinejudge.hwk.domain.HomeworkSubmitStatus.SUBMITTED,
                com.onlinejudge.common.evaluation.EvaluationStatus.ACCEPTED,
                com.onlinejudge.hwk.domain.HomeworkReviewStatus.REVIEWED,
                null, null, null, null, 1, true, NOW, null, null, NOW, NOW, false
        );
    }
}
