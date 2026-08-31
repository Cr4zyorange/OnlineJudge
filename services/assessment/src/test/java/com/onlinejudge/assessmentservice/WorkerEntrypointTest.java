package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.worker.AssessmentWorkerLoop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "assessment.worker.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WorkerEntrypointTest {
    @Autowired AssessmentWorkerLoop loop;
    @Test void workerWorkloadRegistersTheQueueDrivingEntrypoint() { assertThat(loop).isNotNull(); }
}
