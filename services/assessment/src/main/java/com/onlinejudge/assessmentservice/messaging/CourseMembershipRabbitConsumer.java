package com.onlinejudge.assessmentservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.assessmentservice.service.CourseMembershipProjectionService;
import com.onlinejudge.assessmentservice.persistence.CourseMembershipDeadLetterRepository;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/** The Course projection consumer uses explicit acknowledgement after its local inbox transaction. */
@Component
@ConditionalOnProperty(name = "assessment.rabbit.enabled", havingValue = "true")
public class CourseMembershipRabbitConsumer implements SmartLifecycle {
    private final CourseMembershipProjectionService projection; private final CourseMembershipDeadLetterRepository deadLetters; private final ObjectMapper json;
    private final String host, username, password, exchange, queue, snapshotRoutingKey, deadLetterExchange, deadLetterQueue, deadLetterRoutingKey; private final int port;
    private volatile boolean running; private Connection connection; private Channel channel;
    public CourseMembershipRabbitConsumer(CourseMembershipProjectionService projection, CourseMembershipDeadLetterRepository deadLetters, ObjectMapper json,
            @Value("${assessment.rabbit.host:127.0.0.1}") String host, @Value("${assessment.rabbit.port:5672}") int port,
            @Value("${assessment.rabbit.username:guest}") String username, @Value("${assessment.rabbit.password:guest}") String password,
            @Value("${assessment.rabbit.exchange:onlinejudge.events.v2}") String exchange,
            @Value("${assessment.rabbit.course-member-queue:assessment.course-members.v2}") String queue,
            @Value("${assessment.rabbit.course-membership-snapshot-routing-key:onlinejudge.course.membership.snapshot.v2}") String snapshotRoutingKey,
            @Value("${assessment.rabbit.course-member-dead-letter-exchange:onlinejudge.events.dlx.v2}") String deadLetterExchange,
            @Value("${assessment.rabbit.course-member-dead-letter-queue:assessment.course-members.dlq.v2}") String deadLetterQueue,
            @Value("${assessment.rabbit.course-member-dead-letter-routing-key:onlinejudge.course.member.changed.invalid.v2}") String deadLetterRoutingKey) {
        this.projection = projection; this.deadLetters = deadLetters; this.json = json; this.host = host; this.port = port; this.username = username; this.password = password; this.exchange = exchange; this.queue = queue; this.snapshotRoutingKey = snapshotRoutingKey; this.deadLetterExchange = deadLetterExchange; this.deadLetterQueue = deadLetterQueue; this.deadLetterRoutingKey = deadLetterRoutingKey;
    }
    @Override public void start() {
        try {
            var factory = new ConnectionFactory(); factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
            connection = factory.newConnection("assessment-course-member-consumer"); channel = connection.createChannel();
            channel.exchangeDeclare(exchange, "topic", true); channel.exchangeDeclare(deadLetterExchange, "topic", true);
            channel.queueDeclare(deadLetterQueue, true, false, false, null); channel.queueBind(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);
            channel.queueDeclare(queue, true, false, false, Map.of("x-dead-letter-exchange", deadLetterExchange, "x-dead-letter-routing-key", deadLetterRoutingKey));
            channel.queueBind(queue, exchange, "onlinejudge.course.member.changed.v2"); channel.queueBind(queue, exchange, snapshotRoutingKey); channel.basicQos(1);
            channel.basicConsume(queue, false, (tag, message) -> {
                try {
                    var root = json.readTree(message.getBody());
                    if ("course.member.changed.v2".equals(root.path("eventType").asText())) {
                        CourseMembershipEventEnvelope event = CourseMembershipEventEnvelope.parse(root);
                        projection.apply(new CourseMembershipProjectionService.MemberChanged(event.eventId(), event.courseId(), event.userId(), event.membershipStatus(), event.memberVersion()));
                    } else if ("course.membership.snapshot.v2".equals(root.path("eventType").asText())) {
                        CourseMembershipSnapshotEventEnvelope snapshot = CourseMembershipSnapshotEventEnvelope.parse(root);
                        projection.applySnapshot(new CourseMembershipProjectionService.RosterSnapshot(snapshot.eventId(), snapshot.courseId(), snapshot.rosterVersion(),
                                snapshot.members().stream().map(member -> new CourseMembershipProjectionService.RosterMember(member.userId(), member.membershipStatus(), member.memberVersion())).toList()));
                    } else throw new IllegalArgumentException("unsupported Course membership event");
                    // GAP is durable in the Assessment schema; acknowledging prevents a
                    // requeue loop from starving the missing lower aggregate version.
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (IllegalArgumentException invalidEnvelope) {
                    // Untrusted wire data is terminal: never requeue an invalid v1/malformed envelope.
                    // The declared durable DLX retains it for audit and controlled replay.
                    channel.basicNack(message.getEnvelope().getDeliveryTag(), false, false);
                } catch (Exception unavailable) { channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true); }
            }, tag -> { });
            channel.basicConsume(deadLetterQueue, false, (tag, message) -> {
                try {
                    String raw = new String(message.getBody(), StandardCharsets.UTF_8);
                    String eventId = deadLetterId(raw, message.getProperties().getMessageId());
                    deadLetters.capture(eventId, raw, "INVALID_COURSE_MEMBER_ENVELOPE", Instant.now());
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (Exception unavailable) { channel.basicNack(message.getEnvelope().getDeliveryTag(), false, true); }
            }, tag -> { });
            running = true;
        } catch (Exception unavailable) { stop(); }
    }
    @Override public void stop() { running = false; try { if (channel != null) channel.close(); } catch (Exception ignored) { } try { if (connection != null) connection.close(); } catch (Exception ignored) { } }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return 1; }
    private String deadLetterId(String raw, String messageId) {
        try { String eventId = json.readTree(raw).path("eventId").asText(); if (!eventId.isBlank()) return eventId; } catch (Exception ignored) { }
        if (messageId != null && !messageId.isBlank()) return messageId;
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
