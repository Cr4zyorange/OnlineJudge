package com.onlinejudge.contracts;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.common.evaluation.EvaluationResult;
import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.common.evaluation.EvaluationTask;
import com.onlinejudge.common.evaluation.Evaluator;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationType;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewStatus;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmitStatus;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.service.HomeworkSubmissionService;
import com.onlinejudge.lab.domain.LabEvaluation;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabEvaluationRepository;
import com.onlinejudge.lab.domain.LabEvaluationResultRepository;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabExperimentStatus;
import com.onlinejudge.lab.domain.LabSubmission;
import com.onlinejudge.lab.domain.LabSubmissionRepository;
import com.onlinejudge.lab.domain.LabSubmitStatus;
import com.onlinejudge.lab.domain.LabTestcase;
import com.onlinejudge.lab.service.LabEvaluationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #310 C-07 评测完成事件契约：LAB/HWK 评测收敛到终态（成功或 SYSTEM_ERROR）
 * 时必须以稳定幂等键向 LRN 发布完成事件。
 */
class EvaluationCompletionEventContractTest {

    @Test
    void labEvaluationCompletionPublishesStableCompletionEvent() {
        List<NotificationEvent> published = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 10, 0);
        LabExperiment experiment = labExperiment(now);
        LabSubmission submission = labSubmission(now);

        LabSubmissionRepository submissionRepository = mock(LabSubmissionRepository.class);
        LabEvaluationRepository evaluationRepository = mock(LabEvaluationRepository.class);
        LabEvaluationResultRepository resultRepository = mock(LabEvaluationResultRepository.class);
        when(evaluationRepository.findLatestBySubmissionId(701L)).thenReturn(Optional.of(existingLabEvaluation(now)));
        when(evaluationRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LabEvaluationService service = new LabEvaluationService(
                task -> new EvaluationResult(
                        task.taskId(), EvaluationStatus.ACCEPTED, BigDecimal.ONE, "通过",
                        List.of("3"), now
                ),
                submissionRepository,
                evaluationRepository,
                resultRepository,
                published::add
        );

        service.evaluateSubmissionAsync(experiment, submission, submission.codeContent());

        assertThat(published).hasSize(1);
        NotificationEvent event = published.get(0);
        assertThat(event.type()).isEqualTo("LAB_EVALUATION_COMPLETED");
        assertThat(event.idempotencyKey()).isEqualTo("LAB_EVALUATION_701");
        assertThat(event.courseId()).isEqualTo(101L);
        assertThat(event.recipientUserIds()).containsExactly(601L);
        assertThat(event.targetType()).isEqualTo("LAB");
        assertThat(event.targetId()).isEqualTo(301L);
    }

    @Test
    void labEvaluationSystemErrorStillPublishesCompletionEvent() {
        List<NotificationEvent> published = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 10, 0);
        LabExperiment experiment = labExperiment(now);
        LabSubmission submission = labSubmission(now);

        LabSubmissionRepository submissionRepository = mock(LabSubmissionRepository.class);
        LabEvaluationRepository evaluationRepository = mock(LabEvaluationRepository.class);
        LabEvaluationResultRepository resultRepository = mock(LabEvaluationResultRepository.class);
        when(evaluationRepository.findLatestBySubmissionId(701L)).thenReturn(Optional.of(existingLabEvaluation(now)));
        when(evaluationRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LabEvaluationService service = new LabEvaluationService(
                task -> {
                    throw new IllegalStateException("sandbox unavailable");
                },
                submissionRepository,
                evaluationRepository,
                resultRepository,
                published::add
        );

        service.evaluateSubmissionAsync(experiment, submission, submission.codeContent());

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("LAB_EVALUATION_COMPLETED");
        assertThat(published.get(0).idempotencyKey()).isEqualTo("LAB_EVALUATION_701");
        assertThat(published.get(0).recipientUserIds()).containsExactly(601L);
    }

    @Test
    void homeworkCodeEvaluationCompletionPublishesStableCompletionEvent() {
        List<NotificationEvent> published = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 10, 0);
        Homework homework = homework(now);
        HomeworkSubmission submission = homeworkSubmission(now);
        HomeworkEvaluation evaluation = homeworkEvaluation(now);

        HomeworkRepository homeworkRepository = mock(HomeworkRepository.class);
        HomeworkSubmissionRepository submissionRepository = mock(HomeworkSubmissionRepository.class);
        HomeworkEvaluationRepository evaluationRepository = mock(HomeworkEvaluationRepository.class);
        when(homeworkRepository.findById(11L)).thenReturn(Optional.of(homework));
        when(submissionRepository.findById(31L)).thenReturn(Optional.of(submission));
        when(evaluationRepository.claimPending(eq(41L), eq(31L), any())).thenReturn(true);
        when(evaluationRepository.findById(41L)).thenReturn(Optional.of(evaluation));
        when(evaluationRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Evaluator evaluator = task -> new EvaluationResult(
                task.taskId(), EvaluationStatus.ACCEPTED, BigDecimal.ONE, "通过",
                List.of("3"), now
        );

        HomeworkSubmissionService service = new HomeworkSubmissionService(
                homeworkRepository,
                submissionRepository,
                evaluationRepository,
                mock(HomeworkReviewLogRepository.class),
                (courseId, userId) -> true,
                evaluator,
                null,
                event -> { },
                published::add
        );

        service.evaluatePendingCodeSubmission(41L, 31L);

        assertThat(published).hasSize(1);
        NotificationEvent event = published.get(0);
        assertThat(event.type()).isEqualTo("HWK_EVALUATION_COMPLETED");
        assertThat(event.idempotencyKey()).isEqualTo("HWK_EVALUATION_31");
        assertThat(event.courseId()).isEqualTo(101L);
        assertThat(event.recipientUserIds()).containsExactly(601L);
        assertThat(event.targetType()).isEqualTo("HWK");
        assertThat(event.targetId()).isEqualTo(11L);
    }

    @Test
    void homeworkEvaluationSystemErrorStillPublishesCompletionEvent() {
        List<NotificationEvent> published = new ArrayList<>();
        LocalDateTime now = LocalDateTime.of(2026, 8, 28, 10, 0);
        HomeworkSubmission submission = homeworkSubmission(now);
        HomeworkEvaluation evaluation = homeworkEvaluation(now);
        Homework homework = homework(now);

        HomeworkRepository homeworkRepository = mock(HomeworkRepository.class);
        HomeworkSubmissionRepository submissionRepository = mock(HomeworkSubmissionRepository.class);
        HomeworkEvaluationRepository evaluationRepository = mock(HomeworkEvaluationRepository.class);
        when(homeworkRepository.findById(11L)).thenReturn(Optional.of(homework));
        when(submissionRepository.findById(31L)).thenReturn(Optional.of(submission));
        when(evaluationRepository.findById(41L)).thenReturn(Optional.of(evaluation));
        when(evaluationRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HomeworkSubmissionService service = new HomeworkSubmissionService(
                homeworkRepository,
                submissionRepository,
                evaluationRepository,
                mock(HomeworkReviewLogRepository.class),
                (courseId, userId) -> true,
                task -> {
                    throw new UnsupportedOperationException();
                },
                null,
                event -> { },
                published::add
        );

        service.markCodeEvaluationSystemError(41L, 31L);

        assertThat(published).hasSize(1);
        assertThat(published.get(0).type()).isEqualTo("HWK_EVALUATION_COMPLETED");
        assertThat(published.get(0).idempotencyKey()).isEqualTo("HWK_EVALUATION_31");
        assertThat(published.get(0).recipientUserIds()).containsExactly(601L);
    }

    private static LabExperiment labExperiment(LocalDateTime now) {
        return new LabExperiment(
                301L, 101L, null, "实验", "描述", LabExperimentStatus.PUBLISHED,
                now.plusDays(1), 100, List.of(), "python", LabEvaluationMode.DOCKER_IO,
                true, false, 1000, 65536, 501L, now.minusDays(1), false,
                now.minusDays(2), now.minusDays(1),
                List.of(new LabTestcase(11L, 301L, "1 2", "3", 100, true, 1000, 65536, 1, false,
                        now.minusDays(2), now.minusDays(2)))
        );
    }

    private static LabSubmission labSubmission(LocalDateTime now) {
        return new LabSubmission(
                701L, 301L, 601L, "print(1+2)", null, "python",
                LabSubmitStatus.SUBMITTED, EvaluationStatus.PENDING, null, null, 1, true,
                now.minusMinutes(5), now.minusMinutes(5), now.minusMinutes(5), false
        );
    }

    private static LabEvaluation existingLabEvaluation(LocalDateTime now) {
        return new LabEvaluation(
                801L, 701L, EvaluationStatus.PENDING, 0, 0, 1, null, null,
                "等待评测", null, null, now.minusMinutes(4), null,
                now.minusMinutes(4), now.minusMinutes(4)
        );
    }

    private static Homework homework(LocalDateTime now) {
        return new Homework(
                11L, 101L, null, "代码作业", "描述", HomeworkType.CODE,
                HomeworkStatus.PUBLISHED, 100, now.plusDays(1), true, false, true,
                null, 501L, now, false, now, now, List.of(),
                List.of(new HomeworkTestCase(21L, 11L, "1 2", "3", 100, true, 1000, 65536, 1,
                        now.minusDays(2), now.minusDays(2))),
                null
        );
    }

    private static HomeworkSubmission homeworkSubmission(LocalDateTime now) {
        return new HomeworkSubmission(
                31L, 11L, 601L, HomeworkType.CODE, "print(input())", null, null, "python",
                HomeworkSubmitStatus.SUBMITTED, EvaluationStatus.PENDING, HomeworkReviewStatus.NEED_REVIEW,
                null, null, null, null, 1, true, now, null, null, now, now, false
        );
    }

    private static HomeworkEvaluation homeworkEvaluation(LocalDateTime now) {
        return new HomeworkEvaluation(
                41L, 31L, 11L, 601L, HomeworkEvaluationType.CODE_JUDGE, EvaluationStatus.PENDING,
                0, 0, 1, null, null, null, "waiting for evaluation", null, null, null,
                false, null, now, null, now, now
        );
    }
}
