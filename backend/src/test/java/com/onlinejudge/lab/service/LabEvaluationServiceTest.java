package com.onlinejudge.lab.service;

import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.lab.domain.LabEvaluation;
import com.onlinejudge.lab.domain.LabEvaluationCaseResult;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabEvaluationRepository;
import com.onlinejudge.lab.domain.LabEvaluationResultRepository;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import com.onlinejudge.lab.domain.LabTestcase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LabEvaluationServiceTest {

    @Test
    void evaluatorExceptionMarksSubmissionAsSystemErrorAndPreservesEvaluationRecord() {
        RecordingSubmissionRepository submissionRepository = new RecordingSubmissionRepository();
        RecordingEvaluationRepository evaluationRepository = new RecordingEvaluationRepository();
        RecordingEvaluationResultRepository resultRepository = new RecordingEvaluationResultRepository();

        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 12, 0);
        LabExperiment experiment = new LabExperiment(
                301L,
                401L,
                null,
                "异常评测实验",
                "模拟评测器抛异常",
                LabExperimentStatus.PUBLISHED,
                now.plusDays(1),
                100,
                List.of(),
                "python",
                LabEvaluationMode.DOCKER_IO,
                true,
                false,
                1000,
                65536,
                501L,
                now.minusDays(1),
                false,
                now.minusDays(2),
                now.minusDays(1),
                List.of(new LabTestcase(
                        11L,
                        301L,
                        "1 2",
                        "3",
                        100,
                        true,
                        1000,
                        65536,
                        1,
                        false,
                        now.minusDays(2),
                        now.minusDays(2)
                ))
        );
        LabSubmission submission = new LabSubmission(
                701L,
                301L,
                601L,
                "print('boom')",
                null,
                "python",
                LabSubmitStatus.SUBMITTED,
                EvaluationStatus.PENDING,
                null,
                null,
                1,
                true,
                now.minusMinutes(5),
                now.minusMinutes(5),
                now.minusMinutes(5),
                false
        );
        evaluationRepository.current = new LabEvaluation(
                801L,
                701L,
                EvaluationStatus.PENDING,
                0,
                0,
                1,
                null,
                null,
                "等待评测",
                null,
                null,
                now.minusMinutes(4),
                null,
                now.minusMinutes(4),
                now.minusMinutes(4)
        );

        LabEvaluationService service = new LabEvaluationService(
                new ThrowingEvaluator(),
                submissionRepository,
                evaluationRepository,
                resultRepository,
                event -> { }
        );

        service.evaluateSubmissionAsync(experiment, submission, submission.codeContent());

        assertThat(submissionRepository.updated).isNotNull();
        assertThat(submissionRepository.updated.evaluationStatus()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
        assertThat(submissionRepository.updated.autoScore()).isZero();

        assertThat(evaluationRepository.updated).isNotNull();
        assertThat(evaluationRepository.updated.status()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
        assertThat(evaluationRepository.updated.score()).isZero();
        assertThat(evaluationRepository.updated.passedCases()).isZero();
        assertThat(evaluationRepository.updated.totalCases()).isEqualTo(1);
        assertThat(evaluationRepository.updated.feedback()).contains("评测失败");
        assertThat(evaluationRepository.updated.runLog()).contains("sandbox offline");

        assertThat(resultRepository.results).hasSize(1);
        assertThat(resultRepository.results.get(0).status()).isEqualTo(EvaluationStatus.SYSTEM_ERROR);
        assertThat(resultRepository.results.get(0).passed()).isFalse();
        assertThat(resultRepository.results.get(0).testcaseId()).isEqualTo(11L);
        assertThat(resultRepository.results.get(0).message()).contains("sandbox offline");
    }

    private static final class ThrowingEvaluator implements Evaluator {
        @Override
        public EvaluationResult evaluate(EvaluationTask task) {
            throw new IllegalStateException("sandbox offline");
        }
    }

    private static final class RecordingSubmissionRepository implements LabSubmissionRepository {
        private LabSubmission updated;

        @Override
        public LabSubmission save(LabSubmission submission) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LabSubmission update(LabSubmission submission) {
            this.updated = submission;
            return submission;
        }

        @Override
        public Optional<LabSubmission> findById(long submissionId) {
            return Optional.empty();
        }

        @Override
        public Optional<LabSubmission> findLatestFinalByLabIdAndStudentId(long labId, long studentId) {
            return Optional.empty();
        }

        @Override
        public List<LabSubmission> findByLabId(long labId) {
            return List.of();
        }

        @Override
        public List<LabSubmission> findByLabIdAndStudentId(long labId, long studentId) {
            return List.of();
        }
    }

    private static final class RecordingEvaluationRepository implements LabEvaluationRepository {
        private LabEvaluation current;
        private LabEvaluation updated;

        @Override
        public LabEvaluation save(LabEvaluation evaluation) {
            this.current = evaluation;
            this.updated = evaluation;
            return evaluation;
        }

        @Override
        public LabEvaluation update(LabEvaluation evaluation) {
            this.current = evaluation;
            this.updated = evaluation;
            return evaluation;
        }

        @Override
        public Optional<LabEvaluation> findLatestBySubmissionId(long submissionId) {
            return Optional.ofNullable(current);
        }
    }

    private static final class RecordingEvaluationResultRepository implements LabEvaluationResultRepository {
        private List<LabEvaluationCaseResult> results = new ArrayList<>();

        @Override
        public void replaceSubmissionResults(long submissionId, List<LabEvaluationCaseResult> results) {
            this.results = new ArrayList<>(results);
        }

        @Override
        public List<LabEvaluationCaseResult> findBySubmissionId(long submissionId) {
            return List.copyOf(results);
        }
    }
}
