package com.onlinejudge.lrn.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runtime trigger for the durable reconciliation worker.
 *
 * Keeping scheduling outside the worker makes explicit replay tests
 * deterministic and lets test contexts disable only this timer without
 * disabling reconciliation itself.
 */
@Component
@ConditionalOnProperty(
        prefix = "onlinejudge.reliability.reconciliation",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LearningReconciliationScheduler {
    private final LearningReconciliationWorker worker;

    public LearningReconciliationScheduler(LearningReconciliationWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${onlinejudge.reliability.reconciliation.fixed-delay-ms:5000}")
    public void reconcileDueMessages() {
        worker.reconcileDueMessages();
    }
}
