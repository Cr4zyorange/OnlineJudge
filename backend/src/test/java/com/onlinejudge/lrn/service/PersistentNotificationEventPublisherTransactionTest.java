package com.onlinejudge.lrn.service;

import com.onlinejudge.common.event.NotificationEvent;
import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_publisher_tx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=250"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        NotificationService.class,
        PersistentNotificationEventPublisher.class,
        PersistentNotificationEventPublisherTransactionTest.TestConfig.class
})
@EnableTransactionManagement
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(scripts = "file:../database/migrations/20260603_01_create_lrn_notification.sql")
@Sql(statements = "CREATE TABLE IF NOT EXISTS notification_tx_probe (id BIGINT PRIMARY KEY)")
class PersistentNotificationEventPublisherTransactionTest {
    static class TestConfig {
        @Bean
        @Primary
        TestNotificationRepository notificationRepository(JdbcTemplate jdbcTemplate) {
            return new TestNotificationRepository(jdbcTemplate);
        }

        @Bean
        OuterBusinessProbe outerBusinessProbe(
                JdbcTemplate jdbcTemplate,
                NotificationEventPublisher notificationPublisher
        ) {
            return new OuterBusinessProbe(jdbcTemplate, notificationPublisher);
        }

        @Bean(name = "notificationDeliveryExecutor")
        QueuedNotificationExecutor notificationDeliveryExecutor() {
            return new QueuedNotificationExecutor();
        }
    }

    static class TestNotificationRepository extends JdbcNotificationRepository {
        private boolean failNextSave;

        TestNotificationRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        void failNextSave() {
            failNextSave = true;
        }

        @Override
        public Optional<Long> save(
                long userId,
                String type,
                NotificationCreateCommand command,
                String idempotencyKey
        ) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("simulated notification persistence failure");
            }
            return super.save(userId, type, command, idempotencyKey);
        }
    }

    static class OuterBusinessProbe {
        private final JdbcTemplate jdbcTemplate;
        private final NotificationEventPublisher notificationPublisher;
        private int notificationCountAfterPublish;

        OuterBusinessProbe(
                JdbcTemplate jdbcTemplate,
                NotificationEventPublisher notificationPublisher
        ) {
            this.jdbcTemplate = jdbcTemplate;
            this.notificationPublisher = notificationPublisher;
        }

        @Transactional
        public void saveBusinessStateAndPublishNotification(long id, boolean failAfterPublish) {
            jdbcTemplate.update("INSERT INTO notification_tx_probe (id) VALUES (?)", id);
            notificationPublisher.publish(event(id));
            notificationCountAfterPublish = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM lrn_notification",
                    Integer.class
            );
            if (failAfterPublish) {
                throw new IllegalStateException("simulated outer business rollback");
            }
        }

        @Transactional
        public void saveBusinessStateAndPublishRequiredNotification(long id) {
            jdbcTemplate.update("INSERT INTO notification_tx_probe (id) VALUES (?)", id);
            notificationPublisher.publishRequired(event(id));
        }

        int notificationCountAfterPublish() {
            return notificationCountAfterPublish;
        }
    }

    static class QueuedNotificationExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean rejectNext;

        @Override
        public void execute(Runnable command) {
            if (rejectNext) {
                rejectNext = false;
                throw new RejectedExecutionException("simulated notification executor saturation");
            }
            tasks.addLast(command);
        }

        int pendingTaskCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        void rejectNext() {
            rejectNext = true;
        }

        void clear() {
            tasks.clear();
            rejectNext = false;
        }
    }

    @Autowired
    private OuterBusinessProbe outerBusinessProbe;

    @Autowired
    private TestNotificationRepository notificationRepository;

    @Autowired
    private NotificationEventPublisher notificationPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueuedNotificationExecutor notificationDeliveryExecutor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM lrn_notification_status_log");
        jdbcTemplate.update("DELETE FROM lrn_notification");
        jdbcTemplate.update("DELETE FROM notification_tx_probe");
        notificationDeliveryExecutor.clear();
    }

    @Test
    void afterCommitDefersNewTransactionUntilSourceConnectionIsReleased() {
        outerBusinessProbe.saveBusinessStateAndPublishNotification(1L, false);

        assertThat(outerBusinessProbe.notificationCountAfterPublish()).isZero();
        assertThat(probeCount(1L)).isEqualTo(1);
        assertThat(notificationCount()).isZero();
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isEqualTo(1);

        notificationDeliveryExecutor.runNext();

        assertThat(notificationCount()).isEqualTo(1);
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    @Test
    void successfulNotificationIsNotPersistedWhenOuterBusinessTransactionRollsBack() {
        assertThatThrownBy(() -> outerBusinessProbe.saveBusinessStateAndPublishNotification(2L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated outer business rollback");

        assertThat(probeCount(2L)).isZero();
        assertThat(notificationCount()).isZero();
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    @Test
    void notificationFailureDoesNotRollbackOuterBusinessTransaction() {
        notificationRepository.failNextSave();

        assertThatCode(() -> outerBusinessProbe.saveBusinessStateAndPublishNotification(3L, false))
                .doesNotThrowAnyException();

        assertThat(probeCount(3L)).isEqualTo(1);
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isEqualTo(1);

        assertThatCode(notificationDeliveryExecutor::runNext).doesNotThrowAnyException();

        assertThat(notificationCount()).isZero();
    }

    @Test
    void notificationWithoutOuterTransactionIsPersistedImmediately() {
        notificationPublisher.publish(event(4L));

        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void rejectedAfterCommitDispatchDoesNotAffectCommittedSourceTransaction() {
        notificationDeliveryExecutor.rejectNext();

        assertThatCode(() -> outerBusinessProbe.saveBusinessStateAndPublishNotification(7L, false))
                .doesNotThrowAnyException();

        assertThat(probeCount(7L)).isEqualTo(1);
        assertThat(notificationCount()).isZero();
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    @Test
    void nullAndEmptyRecipientEventsDoNotScheduleOrPersistNotifications() {
        notificationPublisher.publish(null);
        notificationPublisher.publish(event(8L, List.of()));
        notificationPublisher.publishRequired(null);
        notificationPublisher.publishRequired(event(9L, List.of()));

        assertThat(notificationCount()).isZero();
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    @Test
    void requiredNotificationFailureRollsBackSourceTransaction() {
        notificationRepository.failNextSave();

        assertThatThrownBy(() -> outerBusinessProbe.saveBusinessStateAndPublishRequiredNotification(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated notification persistence failure");

        assertThat(probeCount(5L)).isZero();
        assertThat(notificationCount()).isZero();
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    @Test
    void requiredNotificationCommitsAtomicallyWithSourceTransaction() {
        outerBusinessProbe.saveBusinessStateAndPublishRequiredNotification(6L);

        assertThat(probeCount(6L)).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(1);
        assertThat(notificationDeliveryExecutor.pendingTaskCount()).isZero();
    }

    private int probeCount(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_tx_probe WHERE id = ?",
                Integer.class,
                id
        );
    }

    private int notificationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lrn_notification",
                Integer.class
        );
    }

    private static NotificationEvent event(long id) {
        return event(id, List.of(601L));
    }

    private static NotificationEvent event(long id, List<Long> recipients) {
        return new NotificationEvent(
                "LRN:TX:" + id,
                "GRADE_PUBLISHED",
                0L,
                recipients,
                "成绩已发布",
                "测试通知事务边界",
                "GRADE_PUBLISH_RECORD",
                id,
                "/courses/101?page=grades",
                LocalDateTime.now()
        );
    }
}
