package com.onlinejudge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.RabbitMqReliabilityConfiguration;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import com.onlinejudge.lrn.repository.LearningEventInboxRepository;
import com.onlinejudge.lrn.repository.LearningReliabilityRepository;
import com.onlinejudge.lrn.service.LearningCourseMemberChangedHandler;
import com.onlinejudge.lrn.service.LearningCourseMembershipSnapshotHandler;
import com.onlinejudge.lrn.service.LearningHomeworkPublishedHandler;
import com.onlinejudge.lrn.service.LearningReliableEventConsumer;
import com.onlinejudge.lrn.service.RabbitMqLearningReliableListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs only from the disposable shell acceptance: the producer is the real
 * independently deployed Course HTTP service, not a Java fixture.  Learning
 * declares and consumes its production Rabbit binding against MySQL 8.4.
 */
@EnabledIfEnvironmentVariable(named = "ONLINEJUDGE_LIVE_COURSE_TO_LEARNING", matches = "true")
@SpringJUnitConfig(classes = CourseServiceToLearningRabbitMySqlLiveTest.LiveConfiguration.class)
@TestPropertySource(properties = {
        "onlinejudge.reliability.rabbitmq.enabled=true",
        "onlinejudge.reliability.publisher.enabled=false",
        "onlinejudge.test.legacy-header-auth=false",
        "onlinejudge.course.schema-initializer.enabled=false",
        "onlinejudge.demo-data.enabled=false",
        "spring.sql.init.mode=never"
})
class CourseServiceToLearningRabbitMySqlLiveTest {
    @Configuration
    @EnableAutoConfiguration
    @Import({
            JdbcNotificationRepository.class,
            LearningCourseMemberProjectionRepository.class,
            LearningEventInboxRepository.class,
            LearningReliabilityRepository.class,
            LearningHomeworkPublishedHandler.class,
            LearningCourseMemberChangedHandler.class,
            LearningCourseMembershipSnapshotHandler.class,
            LearningReliableEventConsumer.class,
            RabbitMqLearningReliableListener.class,
            RabbitMqReliabilityConfiguration.class
    })
    static class LiveConfiguration { }

    @DynamicPropertySource
    static void liveProperties(DynamicPropertyRegistry registry) {
        String mysqlHost = System.getProperty("oj.mysql.host", "127.0.0.1");
        String mysqlPort = System.getProperty("oj.mysql.port", "3306");
        String mysqlDatabase = System.getProperty("oj.mysql.database", "onlinejudge");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysqlHost + ':' + mysqlPort + '/' + mysqlDatabase
                + "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai"
                + "&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> System.getProperty("oj.mysql.username", "onlinejudge"));
        registry.add("spring.datasource.password", () -> System.getProperty("oj.mysql.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.rabbitmq.host", () -> System.getProperty("oj.rabbit.host", "127.0.0.1"));
        registry.add("spring.rabbitmq.port", () -> Integer.parseInt(System.getProperty("oj.rabbit.port", "5672")));
        registry.add("spring.rabbitmq.username", () -> System.getProperty("oj.rabbit.username", "guest"));
        registry.add("spring.rabbitmq.password", () -> System.getProperty("oj.rabbit.password", "guest"));
    }

    @org.springframework.beans.factory.annotation.Autowired private JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired private ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired @Qualifier("reliableRabbitTemplate") private RabbitTemplate rabbit;

    @BeforeEach
    void clearLearningState() {
        jdbc.update("DELETE FROM lrn_notification_status_log");
        jdbc.update("DELETE FROM lrn_notification");
        jdbc.update("DELETE FROM learning_event_inbox");
        jdbc.update("DELETE FROM learning_event_delivery_attempt");
        jdbc.update("DELETE FROM learning_event_dead_letter");
        jdbc.update("DELETE FROM learning_event_reconciliation_request");
        jdbc.update("DELETE FROM learning_deferred_event");
        jdbc.update("DELETE FROM learning_course_member_projection");
        jdbc.update("DELETE FROM learning_course_membership_watermark");
    }

    @Test
    void realCourseHttpOutboxRecoversIntoLearningWatermarkAndOneStudentNotification() throws Exception {
        long courseId = requiredLong("oj.course.id");
        long studentId = requiredLong("oj.course.student-id");

        // Course created and enrolled the student before this listener started.
        // Its durable relay must recover the same source events once this
        // canonical Learning binding exists; no producer fixture is allowed.
        eventuallyCount("four Course events applied through the Learning listener", """
                learning_event_inbox
                 WHERE consumer_name = 'learning'
                   AND event_type IN ('course.member.changed.v2', 'course.membership.snapshot.v2')
                   AND processing_status = 'APPLIED'
                """, 4L);
        assertThat(count("""
                learning_event_inbox
                 WHERE consumer_name = 'learning'
                   AND event_type IN ('course.member.changed.v2', 'course.membership.snapshot.v2')
                """)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_version FROM learning_course_membership_watermark WHERE course_id = ?
                """, Long.class, courseId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                SELECT membership_status FROM learning_course_member_projection
                 WHERE course_id = ? AND user_id = ?
                """, String.class, courseId, studentId)).isEqualTo("ACTIVE");

        String homeworkEventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> homework = new LinkedHashMap<>();
        homework.put("eventId", homeworkEventId);
        homework.put("eventType", "assessment.homework.published.v2");
        homework.put("payloadVersion", 2);
        homework.put("aggregateType", "homework");
        homework.put("aggregateId", courseId + ":712");
        homework.put("aggregateVersion", 1);
        homework.put("occurredAt", Instant.now().toString());
        homework.put("correlationId", correlationId);
        homework.put("payload", Map.of(
                "courseId", String.valueOf(courseId), "homeworkId", "712", "title", "Course route proof",
                "deadline", "2030-01-02T03:04:05Z", "publishedAt", "2030-01-01T03:04:05Z",
                "receiverScope", "COURSE_ACTIVE_STUDENTS"
        ));
        // Course writes raw JSON envelopes.  Use the same wire shape here,
        // rather than asking RabbitTemplate's JSON converter to serialize a
        // Java String as a quoted JSON string.
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(RabbitMqReliabilityConfiguration.EVENTS_EXCHANGE,
                RabbitMqReliabilityConfiguration.HOMEWORK_PUBLISHED_ROUTING_KEY,
                new Message(objectMapper.writeValueAsBytes(homework), properties));

        eventuallyCount("watermark releases the live homework exactly once", """
                lrn_notification WHERE user_id = %d AND source_module = 'HWK' AND source_id = 712
                """.formatted(studentId), 1L);
        assertThat(count("""
                learning_event_inbox WHERE consumer_name = 'learning' AND event_id = '%s' AND processing_status = 'APPLIED'
                """.formatted(homeworkEventId))).isEqualTo(1L);
        assertThat(count("""
                learning_event_inbox
                 WHERE consumer_name = 'learning'
                   AND event_type IN ('course.member.changed.v2', 'course.membership.snapshot.v2')
                   AND correlation_id IS NOT NULL
                """)).isEqualTo(4L);

        System.out.printf("course-to-learning-live courseId=%d studentId=%d sourceEvents=4 watermark=2 homeworkEventId=%s correlationId=%s notifications=1%n",
                courseId, studentId, homeworkEventId, correlationId);
    }

    private long requiredLong(String key) {
        Long value = Long.getLong(key);
        if (value == null || value <= 0) throw new IllegalStateException("missing positive -D" + key);
        return value;
    }

    private long count(String clause) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + clause, Long.class);
        return value == null ? 0L : value;
    }

    private void eventuallyCount(String description, String clause, long expected) throws InterruptedException {
        // The Course relay's bounded backoff can schedule a recovery attempt
        // tens of seconds after the last broker failure, so the acceptance
        // window must absorb a cold listener startup plus that backoff.
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            if (count(clause) == expected) return;
            Thread.sleep(200);
        }
        assertThat(count(clause)).as(description).isEqualTo(expected);
    }
}
