package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_publisher_tx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        NotificationService.class,
        PersistentNotificationEventPublisher.class,
        PersistentNotificationEventPublisherTransactionTest.TestConfig.class
})
@EnableTransactionManagement
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(
        statements = {
                "CREATE TABLE IF NOT EXISTS notification_tx_probe (id BIGINT PRIMARY KEY)",
                "DELETE FROM notification_tx_probe"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class PersistentNotificationEventPublisherTransactionTest {
    static class TestConfig {
        @Bean
        @Primary
        FailingNotificationRepository notificationRepository(JdbcTemplate jdbcTemplate) {
            return new FailingNotificationRepository(jdbcTemplate);
        }

        @Bean
        OuterBusinessProbe outerBusinessProbe(
                JdbcTemplate jdbcTemplate,
                NotificationEventPublisher notificationPublisher
        ) {
            return new OuterBusinessProbe(jdbcTemplate, notificationPublisher);
        }
    }

    static class FailingNotificationRepository extends JdbcNotificationRepository {
        FailingNotificationRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public Optional<Long> save(
                long userId,
                String type,
                NotificationCreateCommand command,
                String idempotencyKey
        ) {
            throw new IllegalStateException("simulated notification persistence failure");
        }
    }

    static class OuterBusinessProbe {
        private final JdbcTemplate jdbcTemplate;
        private final NotificationEventPublisher notificationPublisher;

        OuterBusinessProbe(
                JdbcTemplate jdbcTemplate,
                NotificationEventPublisher notificationPublisher
        ) {
            this.jdbcTemplate = jdbcTemplate;
            this.notificationPublisher = notificationPublisher;
        }

        @Transactional
        public void saveBusinessStateAndPublishNotification() {
            jdbcTemplate.update("INSERT INTO notification_tx_probe (id) VALUES (1)");
            notificationPublisher.publish(new NotificationEvent(
                    "GRD:TX:1",
                    "GRADE_PUBLISHED",
                    0L,
                    List.of(601L),
                    "成绩已发布",
                    "测试通知事务隔离",
                    "GRADE_PUBLISH_RECORD",
                    1L,
                    "/courses/101?page=grades",
                    LocalDateTime.now()
            ));
        }
    }

    @Autowired
    private OuterBusinessProbe outerBusinessProbe;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void notificationFailureDoesNotMarkOuterBusinessTransactionRollbackOnly() {
        assertThatCode(outerBusinessProbe::saveBusinessStateAndPublishNotification)
                .doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_tx_probe WHERE id = 1",
                Integer.class
        )).isEqualTo(1);
    }
}
