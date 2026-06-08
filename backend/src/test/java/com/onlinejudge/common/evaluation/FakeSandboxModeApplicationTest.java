package com.onlinejudge.common.evaluation;

import com.onlinejudge.lab.service.FakeSandboxExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fake_sandbox_mode_application;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false",
        "onlinejudge.evaluation.sandbox.mode=fake"
})
class FakeSandboxModeApplicationTest {
    @Autowired
    private SandboxExecutor sandboxExecutor;

    @Test
    void fakeSandboxModeProvidesExecutorWithoutTestProfile() {
        assertThat(sandboxExecutor).isInstanceOf(FakeSandboxExecutor.class);
    }
}
