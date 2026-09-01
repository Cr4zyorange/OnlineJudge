package com.onlinejudge.courseservice.learning;

import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Course's durable consumer for #306 frozen Assessment/Grade facts plus its own
 * membership snapshots.  The queue is bound by Course itself, so a restarted
 * Course projection catches up on the shared durable queue without a callback
 * or a request-path dependency; duplicate deliveries are idempotent via the
 * event inbox.
 */
@Component
public class LrnFactRabbitConsumer {
    private static final Logger log = LoggerFactory.getLogger(LrnFactRabbitConsumer.class);
    private static final String[] ROUTING_KEYS = {
            "onlinejudge.course.member.changed.v2",
            "onlinejudge.course.membership.snapshot.v2",
            "onlinejudge.course.announcement.published.v2",
            "onlinejudge.assessment.homework.published.v2",
            "onlinejudge.assessment.lab.published.v2",
            "onlinejudge.assessment.evaluation.completed.v2",
            "onlinejudge.grade.published.v2",
            "onlinejudge.grade.review.processed.v2"
    };
    private static final int MAX_MESSAGES_PER_DRAIN = 50;

    private final CourseRabbitProperties rabbit;
    private final LrnEventProjection projection;

    public LrnFactRabbitConsumer(CourseRabbitProperties rabbit, LrnEventProjection projection) {
        this.rabbit = rabbit;
        this.projection = projection;
    }

    @Scheduled(fixedDelayString = "${COURSE_LEARNING_FACTS_POLL_INTERVAL:PT1S}")
    public void poll() {
        drain();
    }

    /** Returns the number of broker deliveries durably handled in this poll. */
    public int drain() {
        if (!rabbit.isEnabled()) return 0;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getPort());
        factory.setUsername(rabbit.getUsername());
        factory.setPassword(rabbit.getPassword());
        factory.setConnectionTimeout(1_000);
        factory.setAutomaticRecoveryEnabled(false);

        try (Connection connection = factory.newConnection("course-learning-facts-consumer");
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.queueDeclare(rabbit.getLearningFactsQueue(), true, false, false, null);
            for (String routingKey : ROUTING_KEYS) {
                channel.queueBind(rabbit.getLearningFactsQueue(), rabbit.getExchange(), routingKey);
            }
            int handled = 0;
            GetResponse delivery;
            while (handled < MAX_MESSAGES_PER_DRAIN
                    && (delivery = channel.basicGet(rabbit.getLearningFactsQueue(), false)) != null) {
                try {
                    projection.consume(new String(delivery.getBody(), StandardCharsets.UTF_8));
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    handled++;
                } catch (IllegalArgumentException malformed) {
                    channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
                    handled++;
                    log.warn("Discarded invalid Learning fact: {}", malformed.getMessage());
                } catch (Exception transientFailure) {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                    log.warn("Deferred Learning fact for retry", transientFailure);
                    return handled;
                }
            }
            return handled;
        } catch (Exception brokerUnavailable) {
            log.debug("Learning fact poll deferred because RabbitMQ is unavailable", brokerUnavailable);
            return 0;
        }
    }
}
