package com.onlinejudge.lrn.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.common.reliability.EventProcessingDecision;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqLearningReliableListenerTest {
    @Mock
    private LearningReliableEventConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void retryPublishConfirmFailureNeverAcknowledgesTheOriginalDelivery() throws Exception {
        ReliableEventEnvelope envelope = envelope();
        when(consumer.deserialize(anyString())).thenReturn(envelope);
        when(consumer.consume(envelope)).thenReturn(EventProcessingDecision.RETRY);
        doThrow(new IOException("broker connection closed before retry confirm"))
                .when(channel).waitForConfirmsOrDie(5_000L);

        new RabbitMqLearningReliableListener(consumer).consume(message(), channel);

        verify(channel).confirmSelect();
        verify(channel).basicPublish(
                ArgumentMatchers.eq(""),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(byte[].class)
        );
        verify(channel, never()).basicAck(73L, false);
        verify(channel).basicNack(73L, false, true);
    }

    private Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(73L);
        properties.setContentType("application/json");
        return new Message("{\"eventId\":\"retry-window\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
    }

    private ReliableEventEnvelope envelope() throws Exception {
        return new ReliableEventEnvelope(
                "retry-window", "assessment.homework.published.v2", 2,
                "assessment-homework", "91", 1,
                Instant.parse("2026-08-30T09:15:30Z"),
                "34c3bdce-e3ff-45b0-8c75-3e46d0e57f5b",
                new ObjectMapper().readTree("{\"courseId\":\"88\"}")
        );
    }
}
