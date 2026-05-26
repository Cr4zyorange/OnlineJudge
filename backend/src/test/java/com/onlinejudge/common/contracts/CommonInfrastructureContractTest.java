package com.onlinejudge.common.contracts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonInfrastructureContractTest {
    @Test
    void exposesSharedEvaluationNotificationStorageAndSourceGradeContracts() throws Exception {
        assertThat(Class.forName("com.onlinejudge.common.evaluation.EvaluationTask")).isRecord();
        assertThat(Class.forName("com.onlinejudge.common.evaluation.EvaluationResult")).isRecord();
        assertThat(Class.forName("com.onlinejudge.common.evaluation.EvaluationStatus").isEnum()).isTrue();
        assertThat(Class.forName("com.onlinejudge.common.evaluation.Evaluator").isInterface()).isTrue();
        assertThat(Class.forName("com.onlinejudge.common.evaluation.SandboxExecutor").isInterface()).isTrue();

        assertThat(Class.forName("com.onlinejudge.common.event.NotificationEvent")).isRecord();
        assertThat(Class.forName("com.onlinejudge.common.event.NotificationEventPublisher").isInterface()).isTrue();

        assertThat(Class.forName("com.onlinejudge.common.storage.FileStorageService").isInterface()).isTrue();
        assertThat(Class.forName("com.onlinejudge.common.storage.StoredFile")).isRecord();

        assertThat(Class.forName("com.onlinejudge.integration.grade.SourceGradeDTO")).isRecord();
        assertThat(Class.forName("com.onlinejudge.integration.grade.SourceGradeClient").isInterface()).isTrue();
    }
}
