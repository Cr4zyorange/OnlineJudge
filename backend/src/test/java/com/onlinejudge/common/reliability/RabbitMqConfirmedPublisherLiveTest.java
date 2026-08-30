package com.onlinejudge.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Disposable-broker verification. The checked-in shell entrypoint starts an
 * ephemeral RabbitMQ and sets ONLINEJUDGE_LIVE_RABBITMQ=true; ordinary Maven
 * runs skip this class instead of assuming a developer broker exists.
 */
@EnabledIfEnvironmentVariable(named = "ONLINEJUDGE_LIVE_RABBITMQ", matches = "true")
@SpringJUnitConfig(classes = RabbitMqConfirmedPublisherLiveTest.LiveConfiguration.class)
@TestPropertySource(properties = {
        "onlinejudge.reliability.rabbitmq.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class RabbitMqConfirmedPublisherLiveTest {
    static final String INTEGRATION_QUEUE = "onlinejudge.reliability.integration.v2";
    static final String INTEGRATION_KEY = "onlinejudge.reliability.integration.v2";

    @Configuration
    @EnableAutoConfiguration
    @Import({RabbitMqReliabilityConfiguration.class, RabbitMqConfirmedEventPublisher.class})
    static class LiveConfiguration {
        @Bean
        Queue integrationQueue() {
            return QueueBuilder.durable(INTEGRATION_QUEUE).build();
        }

        @Bean
        Binding integrationBinding(TopicExchange onlinejudgeEventsExchange, Queue integrationQueue) {
            return BindingBuilder.bind(integrationQueue).to(onlinejudgeEventsExchange).with(INTEGRATION_KEY);
        }
    }

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> System.getProperty("oj.rabbit.host", "127.0.0.1"));
        registry.add("spring.rabbitmq.port", () -> Integer.parseInt(System.getProperty("oj.rabbit.port", "5672")));
        registry.add("spring.rabbitmq.username", () -> System.getProperty("oj.rabbit.username", "guest"));
        registry.add("spring.rabbitmq.password", () -> System.getProperty("oj.rabbit.password", "guest"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ConfirmedEventPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("reliableRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    @Test
    @EnabledIfSystemProperty(named = "oj.rabbit.expected", matches = "available")
    void confirmedPublisherPersistsAndRoutesTheClosedEnvelopeThroughADurableExchange() throws Exception {
        ReliableEventEnvelope envelope = envelope();

        publisher.publish(envelope, INTEGRATION_KEY);

        Message message = rabbitTemplate.receive(INTEGRATION_QUEUE, 5_000);
        assertThat(message).isNotNull();
        assertThat((String) message.getMessageProperties().getHeader("eventId")).isEqualTo(envelope.eventId());
        assertThat((String) message.getMessageProperties().getHeader("correlationId")).isEqualTo(envelope.correlationId());
        assertThat(message.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        assertThat(new ObjectMapper().readTree(message.getBody()).path("eventId").asText()).isEqualTo(envelope.eventId());
        System.out.printf("rabbit-live-confirmed eventId=%s correlationId=%s route=%s%n",
                envelope.eventId(), envelope.correlationId(), INTEGRATION_KEY);
    }

    @Test
    @EnabledIfSystemProperty(named = "oj.rabbit.expected", matches = "unavailable")
    void unavailableBrokerFailsTheConfirmedPublishRatherThanClaimingDelivery() throws Exception {
        ReliableEventEnvelope envelope = envelope();
        assertThatThrownBy(() -> publisher.publish(envelope, INTEGRATION_KEY))
                .isInstanceOf(BrokerUnavailableException.class);
        System.out.printf("rabbit-live-unavailable eventId=%s correlationId=%s route=%s%n",
                envelope.eventId(), envelope.correlationId(), INTEGRATION_KEY);
    }

    private ReliableEventEnvelope envelope() throws Exception {
        String eventId = UUID.randomUUID().toString();
        return new ReliableEventEnvelope(
                eventId,
                "assessment.homework.published.v2",
                2,
                "assessment-homework",
                "91",
                1,
                Instant.now(),
                UUID.randomUUID().toString(),
                new ObjectMapper().readTree("""
                        {"courseId":"88","homeworkId":"91","title":"Reliable messaging homework","deadline":"2026-09-06T16:00:00Z","receiverScope":"COURSE_ACTIVE_STUDENTS","publishedAt":"2026-08-30T09:15:30Z"}
                        """)
        );
    }
}
