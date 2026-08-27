package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Re-dispatches persisted CODE evaluation tasks when the in-process after-commit event is missed.
 */
@Component
@ConditionalOnProperty(
        prefix = "onlinejudge.hwk.evaluation",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HomeworkEvaluationRecovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeworkEvaluationRecovery.class);
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final HomeworkEvaluationRepository evaluationRepository;
    private final HomeworkEvaluationWorker worker;
    private final Clock clock;
    private final LocalDateTime processStartedAt;

    @Autowired
    public HomeworkEvaluationRecovery(
            HomeworkEvaluationRepository evaluationRepository,
            HomeworkEvaluationWorker worker
    ) {
        this(evaluationRepository, worker, Clock.systemDefaultZone());
    }

    HomeworkEvaluationRecovery(
            HomeworkEvaluationRepository evaluationRepository,
            HomeworkEvaluationWorker worker,
            Clock clock
    ) {
        this.evaluationRepository = evaluationRepository;
        this.worker = worker;
        this.clock = clock;
        this.processStartedAt = LocalDateTime.now(clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        LocalDateTime now = LocalDateTime.now(clock);
        int requeued = evaluationRepository.requeueRunningCodeEvaluationsBefore(processStartedAt, now);
        if (requeued > 0) {
            LOGGER.warn("Requeued {} CODE evaluation tasks left RUNNING by a previous process", requeued);
        }
        recoverPendingEvaluations();
    }

    @Scheduled(fixedDelayString = "${onlinejudge.hwk.evaluation.recovery-delay-ms:5000}")
    public void recoverPendingEvaluations() {
        for (HomeworkEvaluation evaluation : evaluationRepository.findPendingCodeEvaluations(RECOVERY_BATCH_SIZE)) {
            try {
                worker.evaluate(new HomeworkEvaluationTaskCreated(evaluation.id(), evaluation.submissionId()));
            } catch (RuntimeException exception) {
                // A saturated executor leaves the durable PENDING record untouched for the next scan.
                LOGGER.warn("Unable to dispatch homework evaluation task {}; it will be retried", evaluation.id(), exception);
            }
        }
    }
}
