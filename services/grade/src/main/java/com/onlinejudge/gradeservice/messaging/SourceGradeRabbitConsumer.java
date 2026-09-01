package com.onlinejudge.gradeservice.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Durable consumer with explicit ack after the Grade inbox/projection transaction commits. */
@Component
@ConditionalOnProperty(name = "grade.rabbit.enabled", havingValue = "true")
public class SourceGradeRabbitConsumer implements SmartLifecycle {
    private final SourceGradeRabbitMessageHandler handler;
    private final String host, username, password, exchange, queue, routingKey, deadLetterExchange, deadLetterQueue, deadLetterRoutingKey;
    private final int port;
    private volatile boolean running;
    private Connection connection;
    private Channel channel;

    public SourceGradeRabbitConsumer(SourceGradeRabbitMessageHandler handler,
            @Value("${grade.rabbit.host:127.0.0.1}") String host, @Value("${grade.rabbit.port:5672}") int port,
            @Value("${grade.rabbit.username:guest}") String username, @Value("${grade.rabbit.password:guest}") String password,
            @Value("${grade.rabbit.exchange:onlinejudge.events.v2}") String exchange,
            @Value("${grade.rabbit.source-queue:grade.source-grades.v2}") String queue,
            @Value("${grade.rabbit.source-routing-key:onlinejudge.assessment.source-grade.changed.v2}") String routingKey,
            @Value("${grade.rabbit.dead-letter-exchange:onlinejudge.events.dlx.v2}") String deadLetterExchange,
            @Value("${grade.rabbit.source-dead-letter-queue:grade.source-grades.dlq.v2}") String deadLetterQueue,
            @Value("${grade.rabbit.source-dead-letter-routing-key:onlinejudge.assessment.source-grade.changed.invalid.v2}") String deadLetterRoutingKey) {
        this.handler = handler; this.host = host; this.port = port; this.username = username; this.password = password;
        this.exchange = exchange; this.queue = queue; this.routingKey = routingKey; this.deadLetterExchange = deadLetterExchange;
        this.deadLetterQueue = deadLetterQueue; this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    @Override
    public void start() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
            factory.setConnectionTimeout(1_000); factory.setHandshakeTimeout(1_000);
            factory.setAutomaticRecoveryEnabled(true); factory.setTopologyRecoveryEnabled(true); factory.setNetworkRecoveryInterval(1_000);
            connection = factory.newConnection("grade-source-grade-consumer");
            channel = connection.createChannel();
            channel.exchangeDeclare(exchange, "topic", true);
            channel.exchangeDeclare(deadLetterExchange, "topic", true);
            channel.queueDeclare(deadLetterQueue, true, false, false, null);
            channel.queueBind(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);
            channel.queueDeclare(queue, true, false, false,
                    Map.of("x-dead-letter-exchange", deadLetterExchange, "x-dead-letter-routing-key", deadLetterRoutingKey));
            channel.queueBind(queue, exchange, routingKey);
            channel.basicQos(1);
            channel.basicConsume(queue, false, (tag, message) -> {
                try {
                    handler.handle(message.getBody());
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (IllegalArgumentException invalidEnvelope) {
                    channel.basicNack(message.getEnvelope().getDeliveryTag(), false, false);
                } catch (Exception temporaryFailure) {
                    channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true);
                }
            }, tag -> running = false);
            running = true;
        } catch (Exception unavailable) {
            stop();
        }
    }

    @Override public void stop() {
        running = false;
        try { if (channel != null) channel.close(); } catch (Exception ignored) { }
        try { if (connection != null) connection.close(); } catch (Exception ignored) { }
    }
    @Override public boolean isRunning() { return running && connection != null && connection.isOpen() && channel != null && channel.isOpen(); }
    @Override public int getPhase() { return 1; }
}
