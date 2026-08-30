package com.onlinejudge.assessmentservice.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.InetAddress;

/** Enabled only in the assessment-worker workload; the API process has no queue-driving schedule. */
@Component
@ConditionalOnProperty(name = "assessment.worker.enabled", havingValue = "true")
public class AssessmentWorkerLoop {
    private final AssessmentWorker worker;
    public AssessmentWorkerLoop(AssessmentWorker worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${assessment.worker.poll-interval:1000}")
    public void poll() {
        String workerId;
        try { workerId = "assessment-worker@" + InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { workerId = "assessment-worker"; }
        worker.runOne(workerId, task -> AssessmentWorker.EvaluationOutcome.successful("ACCEPTED"));
    }
}
