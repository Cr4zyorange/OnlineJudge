package com.onlinejudge.assessmentservice.messaging;

import com.onlinejudge.assessmentservice.persistence.CourseMembershipDeadLetterRepository;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Explicit one-event operational command; replay never occurs merely because a consumer starts. */
@Component
@ConditionalOnExpression("'${assessment.rabbit.replay-course-member-event-id:}'.trim().length() > 0")
public class CourseMembershipDeadLetterReplayCommand implements ApplicationRunner {
    private final CourseMembershipDeadLetterRepository deadLetters; private final String eventId, host, username, password, exchange; private final int port;
    public CourseMembershipDeadLetterReplayCommand(CourseMembershipDeadLetterRepository deadLetters,
            @Value("${assessment.rabbit.replay-course-member-event-id}") String eventId,
            @Value("${assessment.rabbit.host:127.0.0.1}") String host, @Value("${assessment.rabbit.port:5672}") int port,
            @Value("${assessment.rabbit.username}") String username, @Value("${assessment.rabbit.password}") String password,
            @Value("${assessment.rabbit.exchange:onlinejudge.events.v2}") String exchange) {
        this.deadLetters = deadLetters; this.eventId = eventId; this.host = host; this.port = port; this.username = username; this.password = password; this.exchange = exchange;
    }
    @Override public void run(ApplicationArguments ignored) throws Exception {
        var event = deadLetters.find(eventId).orElseThrow(() -> new IllegalArgumentException("course member DLQ event not found"));
        var factory = new ConnectionFactory(); factory.setHost(host); factory.setPort(port); factory.setUsername(username); factory.setPassword(password);
        try (var connection = factory.newConnection("assessment-course-member-dlq-replay"); var channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, "topic", true);
            var properties = new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json").messageId(event.eventId()).type("course.member.changed.v2").build();
            MandatoryRabbitPublisher.publish(channel, exchange, "onlinejudge.course.member.changed.v2", properties,
                    event.payloadJson().getBytes(StandardCharsets.UTF_8), "course member replay");
        }
        deadLetters.markReplayed(event.eventId(), Instant.now());
    }
}
