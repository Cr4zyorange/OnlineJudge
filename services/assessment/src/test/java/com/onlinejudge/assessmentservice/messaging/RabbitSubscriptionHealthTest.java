package com.onlinejudge.assessmentservice.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitSubscriptionHealthTest {
    @Test
    void shutdownOrConsumerCancellationMakesReadinessFalseUntilBothSubscriptionsRecover() {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);
        RabbitSubscriptionHealth health = new RabbitSubscriptionHealth();

        health.subscriptionsEstablished();
        assertThat(health.isHealthy(connection, channel)).isTrue();

        health.unavailable();
        assertThat(health.isHealthy(connection, channel)).isFalse();

        health.recovered();
        assertThat(health.isHealthy(connection, channel)).isTrue();

        health.primaryConsumerCancelled();
        assertThat(health.isHealthy(connection, channel)).isFalse();

        health.recovered();
        health.deadLetterConsumerCancelled();
        assertThat(health.isHealthy(connection, channel)).isFalse();
    }
}
