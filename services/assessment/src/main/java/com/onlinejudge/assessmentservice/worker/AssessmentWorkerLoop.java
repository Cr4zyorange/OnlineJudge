package com.onlinejudge.assessmentservice.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.net.InetAddress;

/** Enabled only in the assessment-worker workload; the API process has no queue-driving schedule. */
@Component
@ConditionalOnProperty(name = "assessment.worker.enabled", havingValue = "true")
public class AssessmentWorkerLoop implements ApplicationRunner {
    private final AssessmentWorker worker;
    private final SandboxEvaluator sandbox;
    public AssessmentWorkerLoop(AssessmentWorker worker, SandboxEvaluator sandbox) { this.worker = worker; this.sandbox = sandbox; }
    @Scheduled(fixedDelayString = "${assessment.worker.poll-interval:1000}")
    public void poll() {
        String workerId;
        try { workerId = "assessment-worker@" + InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { workerId = "assessment-worker"; }
        worker.runOne(workerId, sandbox::evaluate);
    }
    @Override public void run(org.springframework.boot.ApplicationArguments arguments) { poll(); }
}
