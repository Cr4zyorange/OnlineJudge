package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import com.onlinejudge.courseservice.service.CourseOutboxRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires the disposable RabbitMQ mapped by the runtime acceptance command.
 * The Spring context also schedules the production relay and consumer, so
 * these tests drive hand-built relay instances with a local Rabbit
 * configuration while the shared bean stays disabled.  Otherwise the
 * scheduled relay becomes a third claimant and the two-relay race is not
 * deterministic.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.rabbit", matches = "true")
class CourseOutboxRelayRecoveryTest {
    @Autowired private CourseOutboxRepository outbox;
    @Autowired private CourseRabbitProperties rabbit;
    @Autowired private JdbcTemplate jdbcTemplate;

    private CourseRabbitProperties relayRabbit;

    @BeforeEach
    void before() {
        rabbit.setEnabled(false);
        rabbit.setHost("127.0.0.1");
        rabbit.setPort(Integer.getInteger("course.test.rabbit.port", 33327));
        relayRabbit = new CourseRabbitProperties();
        relayRabbit.setEnabled(true);
        relayRabbit.setHost("127.0.0.1");
        relayRabbit.setPort(Integer.getInteger("course.test.rabbit.port", 33327));
        relayRabbit.setUsername(rabbit.getUsername());
        relayRabbit.setPassword(rabbit.getPassword());
        relayRabbit.setExchange(rabbit.getExchange());
        jdbcTemplate.update("DELETE FROM course_event_outbox");
    }

    @AfterEach
    void after() {
        rabbit.setEnabled(false);
    }

    @Test
    void unavailableBrokerLeavesCourseFactPendingAndRecoveryPublishesTheSameDurableEvent() throws Exception {
        outbox.append("course.member.changed.v2", "course-member", "42:99", 1,
                "be087a98-88e6-4dac-9486-5c50b4231b4d",
                Map.of("courseId", "42", "userId", "99", "membershipStatus", "ACTIVE", "memberVersion", 1));
        outbox.append("course.announcement.published.v2", "course-announcement", "91", 1,
                "b3501574-e50c-4e20-9ee7-7ae7efde5c85",
                Map.of("courseId", "42", "announcementId", "91", "publishedAt", "2026-08-31T00:00:00Z"));
        relayRabbit.setPort(1);
        relay().relay();

        assertThat(jdbcTemplate.queryForList("SELECT delivery_status FROM course_event_outbox", String.class)).containsOnly("RETRY");
        assertThat(jdbcTemplate.queryForList("SELECT attempt_count FROM course_event_outbox", Integer.class)).containsOnly(1);

        jdbcTemplate.update("UPDATE course_event_outbox SET next_attempt_at = CURRENT_TIMESTAMP");
        relayRabbit.setPort(Integer.getInteger("course.test.rabbit.port", 33327));
        relay().relay();

        assertThat(jdbcTemplate.queryForList("SELECT delivery_status FROM course_event_outbox", String.class)).containsOnly("RETRY");
        assertThat(jdbcTemplate.queryForList("SELECT attempt_count FROM course_event_outbox", Integer.class)).containsOnly(2);

        try (Connection connection = connection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.queueDeclare("course-outbox-recovery", true, false, false, null);
            channel.queueBind("course-outbox-recovery", rabbit.getExchange(), "onlinejudge.course.member.changed.v2");
            channel.queueBind("course-outbox-recovery", rabbit.getExchange(), "onlinejudge.course.announcement.published.v2");
        }
        jdbcTemplate.update("UPDATE course_event_outbox SET next_attempt_at = CURRENT_TIMESTAMP");
        relay().relay();

        assertThat(jdbcTemplate.queryForList("SELECT delivery_status FROM course_event_outbox", String.class)).containsOnly("PUBLISHED");
        assertThat(jdbcTemplate.queryForList("SELECT attempt_count FROM course_event_outbox", Integer.class)).containsOnly(2);
    }

    @Test
    void twoConcurrentRelayInstancesClaimAndPublishTheDurableEventExactlyOnce() throws Exception {
        outbox.append("course.member.changed.v2", "course-member", "43:100", 1,
                "915f53ed-8752-4b11-aed9-dc4dd8ca032d",
                Map.of("courseId", "43", "userId", "100", "membershipStatus", "ACTIVE", "memberVersion", 1));
        try (Connection connection = connection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.queueDeclare("course-outbox-two-relay", true, false, false, null);
            channel.queueBind("course-outbox-two-relay", rabbit.getExchange(), "onlinejudge.course.member.changed.v2");
        }

        CourseOutboxRelay first = relay();
        CourseOutboxRelay second = relay();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> firstResult = pool.submit(() -> relayAfterStart(first, ready, start));
            Future<Integer> secondResult = pool.submit(() -> relayAfterStart(second, ready, start));
            ready.await();
            start.countDown();
            assertThat(firstResult.get() + secondResult.get()).isEqualTo(1);
        }

        try (Connection connection = connection(); Channel channel = connection.createChannel()) {
            assertThat(channel.queueDeclarePassive("course-outbox-two-relay").getMessageCount()).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForMap("SELECT delivery_status, attempt_count, lease_owner FROM course_event_outbox"))
                .containsEntry("delivery_status", "PUBLISHED").containsEntry("attempt_count", 0).containsEntry("lease_owner", null);
    }

    private CourseOutboxRelay relay() {
        return new CourseOutboxRelay(outbox, relayRabbit, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private int relayAfterStart(CourseOutboxRelay relay, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return relay.relayOnce();
    }

    private Connection connection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost()); factory.setPort(rabbit.getPort());
        factory.setUsername(rabbit.getUsername()); factory.setPassword(rabbit.getPassword());
        return factory.newConnection("course-outbox-recovery-test");
    }
}
