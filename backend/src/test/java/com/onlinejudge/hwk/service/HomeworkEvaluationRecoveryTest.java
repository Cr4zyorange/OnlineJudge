package com.onlinejudge.hwk.service;

import com.onlinejudge.common.evaluation.EvaluationStatus;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkEvaluationType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeworkEvaluationRecoveryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T08:30:00Z"), ZoneOffset.UTC);

    @Test
    void redispatchesPendingTaskWhenInitialAfterCommitDispatchNeverRan() {
        HomeworkEvaluationRepository evaluationRepository = mock(HomeworkEvaluationRepository.class);
        HomeworkEvaluationWorker worker = mock(HomeworkEvaluationWorker.class);
        HomeworkEvaluation pending = pendingEvaluation(41L, 31L);
        when(evaluationRepository.findPendingCodeEvaluations(100)).thenReturn(List.of(pending));
        HomeworkEvaluationRecovery recovery = new HomeworkEvaluationRecovery(evaluationRepository, worker, CLOCK);

        recovery.recoverPendingEvaluations();

        verify(worker).evaluate(new HomeworkEvaluationTaskCreated(41L, 31L));
    }

    @Test
    void requeuesRunningTaskLeftByPreviousProcessBeforeRedispatchingIt() {
        HomeworkEvaluationRepository evaluationRepository = mock(HomeworkEvaluationRepository.class);
        HomeworkEvaluationWorker worker = mock(HomeworkEvaluationWorker.class);
        HomeworkEvaluation pending = pendingEvaluation(42L, 32L);
        when(evaluationRepository.findPendingCodeEvaluations(100)).thenReturn(List.of(pending));
        HomeworkEvaluationRecovery recovery = new HomeworkEvaluationRecovery(evaluationRepository, worker, CLOCK);

        recovery.recoverAfterRestart();

        LocalDateTime restartedAt = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        verify(evaluationRepository).requeueRunningCodeEvaluationsBefore(restartedAt, restartedAt);
        verify(worker).evaluate(new HomeworkEvaluationTaskCreated(42L, 32L));
    }

    private HomeworkEvaluation pendingEvaluation(long evaluationId, long submissionId) {
        LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        return new HomeworkEvaluation(
                evaluationId,
                submissionId,
                11L,
                601L,
                HomeworkEvaluationType.CODE_JUDGE,
                EvaluationStatus.PENDING,
                0,
                0,
                1,
                null,
                null,
                null,
                "waiting for evaluation",
                null,
                null,
                null,
                false,
                null,
                now,
                null,
                now,
                now
        );
    }
}
