package com.onlinejudge.assessmentservice.worker;

import com.onlinejudge.assessmentservice.messaging.CourseMembershipRabbitConsumer;
import com.onlinejudge.assessmentservice.messaging.IdentitySecurityVersionRabbitConsumer;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The worker workload is ready only when its local lease database and both
 * broker consumers are usable.  The API deliberately has no dependency on
 * this marker: it can keep accepting its local submission/outbox transaction
 * while RabbitMQ is unavailable.
 */
@Component
@ConditionalOnProperty(name = {"assessment.worker.enabled", "assessment.rabbit.enabled"}, havingValue = "true")
public class AssessmentWorkerReadiness implements ApplicationRunner {
    private static final Path MARKER = Path.of("/tmp/assessment-worker-ready");

    private final JdbcTemplate jdbc;
    private final CourseMembershipRabbitConsumer courseMembers;
    private final IdentitySecurityVersionRabbitConsumer securityVersions;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public AssessmentWorkerReadiness(JdbcTemplate jdbc, CourseMembershipRabbitConsumer courseMembers,
            IdentitySecurityVersionRabbitConsumer securityVersions,
            @Value("${assessment.rabbit.host:127.0.0.1}") String host,
            @Value("${assessment.rabbit.port:5672}") int port,
            @Value("${assessment.rabbit.username:guest}") String username,
            @Value("${assessment.rabbit.password:guest}") String password) {
        this.jdbc = jdbc;
        this.courseMembers = courseMembers;
        this.securityVersions = securityVersions;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        refresh();
    }

    @Scheduled(fixedDelayString = "${assessment.worker.readiness-interval:5000}")
    public void refresh() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            if (!courseMembers.isRunning() || !securityVersions.isRunning()) throw new IllegalStateException("Rabbit consumer is not running");
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            // Do not let a dead broker leave yesterday's marker in place for
            // the client's minute-long default connect timeout.
            factory.setConnectionTimeout(1_000);
            factory.setHandshakeTimeout(1_000);
            try (Connection ignored = factory.newConnection("assessment-worker-readiness")) {
                Files.writeString(MARKER, "ready\n");
            }
        } catch (Exception unavailable) {
            try { Files.deleteIfExists(MARKER); } catch (Exception ignored) { }
        }
    }
}
