package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.persistence.CourseEventInboxRepository;
import com.onlinejudge.courseservice.service.IdentitySecurityVersionRabbitConsumer;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the production Course-owned Rabbit binding, not a fixture call to the inbox. */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.rabbit", matches = "true")
class IdentitySecurityVersionRabbitConsumerTest {
    @Autowired private CourseRabbitProperties rabbit;
    @Autowired private IdentitySecurityVersionRabbitConsumer consumer;
    @Autowired private CourseEventInboxRepository inbox;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void before() {
        rabbit.setEnabled(true);
        rabbit.setHost("127.0.0.1");
        rabbit.setPort(Integer.getInteger("course.test.rabbit.port", 33327));
        jdbcTemplate.update("DELETE FROM event_inbox");
    }

    @AfterEach
    void after() {
        rabbit.setEnabled(false);
    }

    @Test
    void canonicalIdentityEventFlowsThroughRabbitIntoDurableCourseSecurityWatermark() throws Exception {
        consumer.drain(); // declares Course's durable binding before Identity publishes
        try (Connection connection = connection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.basicPublish(rabbit.getExchange(), "onlinejudge.identity.security-version.changed.v2", null,
                    envelope("identity-security-9201-v2", 2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        assertThat(consumer.drain()).isEqualTo(1);
        assertThat(inbox.minimumSecurityVersion(9201)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM event_inbox WHERE event_id = 'identity-security-9201-v2'", String.class))
                .isEqualTo("APPLIED");
    }

    private Connection connection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost()); factory.setPort(rabbit.getPort());
        factory.setUsername(rabbit.getUsername()); factory.setPassword(rabbit.getPassword());
        return factory.newConnection("course-identity-security-consumer-test");
    }

    private String envelope(String eventId, long securityVersion) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", "identity.security-version.changed.v2");
        event.put("payloadVersion", 2);
        event.put("aggregateType", "identity-user");
        event.put("aggregateId", "9201");
        event.put("aggregateVersion", securityVersion);
        event.put("occurredAt", Instant.parse("2026-08-31T00:00:00Z").toString());
        event.put("correlationId", "7b7185b0-52f9-417e-95f6-3d620a22f2b8");
        event.put("payload", Map.of("userId", "9201", "securityVersion", securityVersion, "changeReason", "LOGOUT"));
        return objectMapper.writeValueAsString(event);
    }
}
