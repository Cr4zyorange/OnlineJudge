package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.service.CourseMembershipProjectionService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** The Course projection consumer uses explicit acknowledgement after its local inbox transaction. */
@Component
@ConditionalOnProperty(name = "assessment.rabbit.enabled", havingValue = "true")
public class CourseMembershipRabbitConsumer implements SmartLifecycle {
    private final CourseMembershipProjectionService projection; private final ObjectMapper json;
    private final String host, username, password, exchange, queue; private final int port;
    private volatile boolean running; private Connection connection; private Channel channel;
    public CourseMembershipRabbitConsumer(CourseMembershipProjectionService projection, ObjectMapper json,
            @Value("${assessment.rabbit.host:127.0.0.1}") String host, @Value("${assessment.rabbit.port:5672}") int port,
            @Value("${assessment.rabbit.username:guest}") String username, @Value("${assessment.rabbit.password:guest}") String password,
            @Value("${assessment.rabbit.exchange:assessment.events.v2}") String exchange,
            @Value("${assessment.rabbit.course-member-queue:assessment.course-members.v2}") String queue) {
        this.projection = projection; this.json = json; this.host = host; this.port = port; this.username = username; this.password = password; this.exchange = exchange; this.queue = queue;
    }
    @Override public void start() {
        try {
            var factory = new ConnectionFactory(); factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
            connection = factory.newConnection("assessment-course-member-consumer"); channel = connection.createChannel();
            channel.exchangeDeclare(exchange, "topic", true); channel.queueDeclare(queue, true, false, false, null);
            channel.queueBind(queue, exchange, "course.member.changed.v2"); channel.basicQos(1);
            channel.basicConsume(queue, false, (tag, message) -> {
                try {
                    JsonNode root = json.readTree(message.getBody()); JsonNode payload = root.has("payload") ? root.get("payload") : root;
                    String eventId = root.path("eventId").asText(message.getProperties().getMessageId());
                    var decision = projection.apply(new CourseMembershipProjectionService.MemberChanged(eventId, payload.path("courseId").asText(),
                            payload.path("userId").asText(), payload.path("membershipStatus").asText(), payload.path("memberVersion").asLong()));
                    if ("GAP".equals(decision.decision())) channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true);
                    else channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (Exception rejected) { channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true); }
            }, tag -> { });
            running = true;
        } catch (Exception unavailable) { stop(); }
    }
    @Override public void stop() { running = false; try { if (channel != null) channel.close(); } catch (Exception ignored) { } try { if (connection != null) connection.close(); } catch (Exception ignored) { } }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return 1; }
}
