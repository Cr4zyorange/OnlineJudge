package com.onlinejudge.assessmentservice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConsumerRecoveryContractTest {
    @Test
    void durableProjectionConsumersExplicitlyRecoverSubscriptionsAndExposeShutdownToReadiness() throws Exception {
        Path root = Path.of("..", "..").toAbsolutePath().normalize();
        for (Path consumer : List.of(
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipRabbitConsumer.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionRabbitConsumer.java"))) {
            String source = Files.readString(consumer);
            assertThat(source).as(consumer.toString()).contains(
                    "setAutomaticRecoveryEnabled(true)",
                    "setTopologyRecoveryEnabled(true)",
                    "setNetworkRecoveryInterval(1_000)",
                    "addShutdownListener",
                    "addRecoveryListener",
                    "RabbitSubscriptionHealth");
        }
    }
}
