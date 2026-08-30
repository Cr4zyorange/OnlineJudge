package com.onlinejudge.courseservice.service;

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
 * Course's durable consumer entrypoint for Identity revocation facts.  The
 * queue is bound by Course itself, so a restarted Course projection can catch
 * up without an Identity callback or a request-path dependency.
 */
@Component
public class IdentitySecurityVersionRabbitConsumer {
    private static final Logger log = LoggerFactory.getLogger(IdentitySecurityVersionRabbitConsumer.class);
    private static final String ROUTING_KEY = "identity.security-version.changed.v2";
    private static final int MAX_MESSAGES_PER_DRAIN = 50;

    private final CourseRabbitProperties rabbit;
    private final IdentitySecurityVersionEventConsumer consumer;

    public IdentitySecurityVersionRabbitConsumer(CourseRabbitProperties rabbit, IdentitySecurityVersionEventConsumer consumer) {
        this.rabbit = rabbit;
        this.consumer = consumer;
    }

    @Scheduled(fixedDelayString = "${COURSE_IDENTITY_SECURITY_VERSION_POLL_INTERVAL:PT1S}")
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

        try (Connection connection = factory.newConnection("course-identity-security-version-consumer");
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(rabbit.getExchange(), "topic", true);
            channel.queueDeclare(rabbit.getIdentitySecurityVersionQueue(), true, false, false, null);
            channel.queueBind(rabbit.getIdentitySecurityVersionQueue(), rabbit.getExchange(), ROUTING_KEY);

            int handled = 0;
            GetResponse delivery;
            while (handled < MAX_MESSAGES_PER_DRAIN
                    && (delivery = channel.basicGet(rabbit.getIdentitySecurityVersionQueue(), false)) != null) {
                try {
                    consumer.consume(new String(delivery.getBody(), StandardCharsets.UTF_8));
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    handled++;
                } catch (IllegalArgumentException malformed) {
                    // A malformed non-canonical event can never become valid on retry.
                    channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
                    handled++;
                    log.warn("Discarded invalid Identity security-version event: {}", malformed.getMessage());
                } catch (Exception transientFailure) {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                    log.warn("Deferred Identity security-version event for retry", transientFailure);
                    return handled;
                }
            }
            return handled;
        } catch (Exception brokerUnavailable) {
            log.debug("Identity security-version poll deferred because RabbitMQ is unavailable", brokerUnavailable);
            return 0;
        }
    }
}
