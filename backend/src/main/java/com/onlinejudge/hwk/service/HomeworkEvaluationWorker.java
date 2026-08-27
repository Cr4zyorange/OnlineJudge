package com.onlinejudge.hwk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HomeworkEvaluationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeworkEvaluationWorker.class);

    private final HomeworkSubmissionService submissionService;

    public HomeworkEvaluationWorker(HomeworkSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Async("homeworkEvaluationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evaluate(HomeworkEvaluationTaskCreated task) {
        try {
            submissionService.evaluatePendingCodeSubmission(task.evaluationId(), task.submissionId());
        } catch (RuntimeException exception) {
            LOGGER.error("Homework evaluation task {} failed", task.evaluationId(), exception);
            submissionService.markCodeEvaluationSystemError(task.evaluationId(), task.submissionId());
        }
    }
}
