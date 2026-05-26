package com.onlinejudge.lab.service;

import com.onlinejudge.common.event.NoopNotificationEventPublisher;
import com.onlinejudge.integration.course.CoursePermissionClient;
import com.onlinejudge.lab.domain.CreateLabExperimentCommand;
import com.onlinejudge.lab.domain.LabEvaluationMode;
import com.onlinejudge.lab.domain.LabExperiment;
import com.onlinejudge.lab.domain.LabTestcase;
import com.onlinejudge.lab.domain.LabTestcaseDraft;
import com.onlinejudge.lab.domain.UpdateLabExperimentCommand;
import com.onlinejudge.lab.repository.JdbcLabExperimentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab_experiment_tx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        LabExperimentService.class,
        LabExperimentTransactionTest.TestConfig.class
})
@EnableTransactionManagement
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(
        scripts = "file:../database/migrations/20260525_02_create_lab_experiment.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = {
                "DELETE FROM lab_testcase",
                "DELETE FROM lab_experiment"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class LabExperimentTransactionTest {
    static class TestConfig {
        @Bean
        @Primary
        FaultInjectingLabExperimentRepository labExperimentRepository(JdbcTemplate jdbcTemplate) {
            return new FaultInjectingLabExperimentRepository(jdbcTemplate);
        }

        @Bean
        CoursePermissionClient coursePermissionClient() {
            return new CoursePermissionClient() {
                @Override
                public boolean canManageCourse(long courseId, long userId) {
                    return true;
                }

                @Override
                public boolean canViewCourse(long courseId, long userId) {
                    return true;
                }
            };
        }

        @Bean
        NoopNotificationEventPublisher notificationEventPublisher() {
            return new NoopNotificationEventPublisher();
        }
    }

    static class FaultInjectingLabExperimentRepository extends JdbcLabExperimentRepository {
        private final AtomicInteger testcaseInsertCount = new AtomicInteger();
        private volatile boolean failOnSecondInsert;

        FaultInjectingLabExperimentRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        void failOnSecondInsert() {
            this.failOnSecondInsert = true;
            this.testcaseInsertCount.set(0);
        }

        void resetFailureMode() {
            this.failOnSecondInsert = false;
            this.testcaseInsertCount.set(0);
        }

        @Override
        protected void insertTestcase(long labId, LabTestcase testcase, LocalDateTime now) {
            int current = testcaseInsertCount.incrementAndGet();
            if (failOnSecondInsert && current == 2) {
                throw new IllegalStateException("simulated testcase persistence failure");
            }
            super.insertTestcase(labId, testcase, now);
        }
    }

    @Autowired
    private LabExperimentService service;

    @Autowired
    private FaultInjectingLabExperimentRepository repository;

    @Test
    void createRollsBackExperimentWhenSecondTestcaseInsertFails() {
        repository.failOnSecondInsert();

        assertThatThrownBy(() -> service.createLab(101L, 501L, createCommand(
                "事务创建回滚",
                List.of(
                        testcase("1 1", "2", true, 1),
                        testcase("2 2", "4", false, 2)
                )
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated testcase persistence failure");

        assertThat(repository.findByCourseId(101L, null)).isEmpty();
    }

    @Test
    void updateRollsBackDeletedAndReplacedTestcasesWhenInsertFails() {
        repository.resetFailureMode();
        LabExperiment created = service.createLab(202L, 501L, createCommand(
                "事务更新回滚",
                List.of(
                        testcase("3 4", "7", true, 1),
                        testcase("5 6", "11", false, 2)
                )
        ));

        repository.failOnSecondInsert();

        assertThatThrownBy(() -> service.updateLab(created.id(), 501L, updateCommand(
                "事务更新回滚-修订",
                List.of(
                        testcase("10 10", "20", true, 1),
                        testcase("20 20", "40", false, 2)
                )
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated testcase persistence failure");

        Optional<LabExperiment> reloaded = repository.findById(created.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.orElseThrow().title()).isEqualTo("事务更新回滚");
        assertThat(reloaded.orElseThrow().testcases())
                .extracting(LabTestcase::expectedOutput)
                .containsExactly("7", "11");
    }

    private CreateLabExperimentCommand createCommand(String title, List<LabTestcaseDraft> testcases) {
        LocalDateTime deadline = LocalDateTime.now().plusDays(7);
        return new CreateLabExperimentCommand(
                null,
                title,
                "事务测试说明",
                deadline,
                100,
                List.of(),
                "java",
                LabEvaluationMode.DOCKER_IO,
                true,
                false,
                60000,
                262144,
                testcases
        );
    }

    private UpdateLabExperimentCommand updateCommand(String title, List<LabTestcaseDraft> testcases) {
        LocalDateTime deadline = LocalDateTime.now().plusDays(10);
        return new UpdateLabExperimentCommand(
                null,
                title,
                "事务更新测试说明",
                deadline,
                120,
                List.of(99L),
                "java,python",
                LabEvaluationMode.MIXED,
                false,
                true,
                90000,
                524288,
                testcases
        );
    }

    private LabTestcaseDraft testcase(String input, String output, boolean isPublic, int orderNum) {
        return new LabTestcaseDraft(
                input,
                output,
                50,
                isPublic,
                1000,
                65536,
                orderNum
        );
    }
}
