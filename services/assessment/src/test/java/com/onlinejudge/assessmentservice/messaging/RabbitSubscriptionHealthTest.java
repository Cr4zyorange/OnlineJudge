package com.onlinejudge.assessmentservice.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
class RabbitSubscriptionHealthTest {
    @Test
    void shutdownOrConsumerCancellationMakesReadinessFalseUntilBothSubscriptionsRecover() {
        Connection connection = openInterface(Connection.class);
        Channel channel = openInterface(Channel.class);
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

    private static <T> T openInterface(Class<T> type) {
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (ignored, method, args) -> {
                    if (method.getName().equals("isOpen")) return true;
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    return null;
                });
        return type.cast(proxy);
    }
}
