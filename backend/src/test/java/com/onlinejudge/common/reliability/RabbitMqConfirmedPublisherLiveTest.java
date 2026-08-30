package com.onlinejudge.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.lrn.service.LearningReliableEventConsumer;
import com.onlinejudge.lrn.service.RabbitMqLearningReliableListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        var deliveredPayload = new ObjectMapper().readTree(message.getBody());
        assertThat(deliveredPayload.path("eventId").asText()).isEqualTo(envelope.eventId());
        assertThat(Instant.parse(deliveredPayload.path("occurredAt").asText()))
                .isEqualTo(envelope.occurredAt());
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

    @Test
    @EnabledIfSystemProperty(named = "oj.rabbit.expected", matches = "retry-window")
    void brokerPauseBetweenRetryPublishAndOriginalAckLeavesTheSourceDeliveryRedeliverable() throws Exception {
        String containerName = System.getProperty("oj.rabbit.docker-container", "");
        assertThat(containerName).as("the live harness must name its disposable broker").isNotBlank();

        ReliableEventEnvelope envelope = envelope();
        String sourceQueue = "onlinejudge.reliability.retry-window." + UUID.randomUUID();
        AtomicBoolean paused = new AtomicBoolean(false);
        ConnectionFactory factory = clientFactory();

        try (Connection firstConnection = factory.newConnection("retry-window-first")) {
            Channel deliveryChannel = firstConnection.createChannel();
            deliveryChannel.queueDeclare(sourceQueue, false, false, true, null);
            deliveryChannel.basicPublish("", sourceQueue,
                    new com.rabbitmq.client.AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                    ("{\"eventId\":\"" + envelope.eventId() + "\"}").getBytes(StandardCharsets.UTF_8));
            GetResponse delivery = deliveryChannel.basicGet(sourceQueue, false);
            assertThat(delivery).isNotNull();

            LearningReliableEventConsumer retryingConsumer = mock(LearningReliableEventConsumer.class);
            when(retryingConsumer.deserialize(anyString())).thenReturn(envelope);
            when(retryingConsumer.consume(envelope)).thenReturn(EventProcessingDecision.RETRY);
            RabbitMqLearningReliableListener listener = new RabbitMqLearningReliableListener(retryingConsumer);
            Channel observedChannel = mock(Channel.class, delegatesTo(deliveryChannel));
            doAnswer(invocation -> {
                deliveryChannel.confirmSelect();
                docker("pause", containerName);
                paused.set(true);
                return null;
            }).when(observedChannel).confirmSelect();

            MessageProperties properties = new MessageProperties();
            properties.setContentType("application/json");
            properties.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
            try {
                assertThatThrownBy(() -> listener.consume(new Message(delivery.getBody(), properties), observedChannel))
                        .isInstanceOf(RuntimeException.class);
            } finally {
                if (paused.get()) {
                    docker("unpause", containerName);
                }
            }
            verify(observedChannel, never()).basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            try {
                if (deliveryChannel.isOpen()) {
                    deliveryChannel.close();
                }
            } catch (Exception expectedClosedChannel) {
                // The broker may have already closed this channel after the
                // forced confirmation timeout; closing the connection below
                // still releases the source delivery for redelivery.
            }
        }

        try (Connection secondConnection = factory.newConnection("retry-window-second");
             Channel recoveryChannel = secondConnection.createChannel()) {
            GetResponse recovered = receiveEventually(recoveryChannel, sourceQueue);
            assertThat(recovered).isNotNull();
            assertThat(recovered.getEnvelope().isRedeliver()).isTrue();
            System.out.printf("rabbit-live-retry-window eventId=%s correlationId=%s originalAck=0 redeliveries=1%n",
                    envelope.eventId(), envelope.correlationId());
        }
    }

    private ConnectionFactory clientFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getProperty("oj.rabbit.host", "127.0.0.1"));
        factory.setPort(Integer.parseInt(System.getProperty("oj.rabbit.port", "5672")));
        factory.setUsername(System.getProperty("oj.rabbit.username", "guest"));
        factory.setPassword(System.getProperty("oj.rabbit.password", "guest"));
        factory.setConnectionTimeout(5_000);
        return factory;
    }

    private GetResponse receiveEventually(Channel channel, String queue) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            GetResponse response = channel.basicGet(queue, true);
            if (response != null) {
                return response;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private void docker(String action, String containerName) throws Exception {
        Process process = new ProcessBuilder("docker", action, containerName)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("docker %s %s: %s", action, containerName, output).isZero();
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
