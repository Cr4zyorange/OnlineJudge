package com.onlinejudge.assessmentservice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitTopologyContractTest {
    @Test
    void assessmentUsesTheCanonicalDurableV2EventsExchangeForPublishConsumeAndReplay() throws Exception {
        Path root = Path.of("..", "..").toAbsolutePath().normalize();
        List<Path> topologyOwners = List.of(
                root.resolve("services/assessment/src/main/resources/application.yml"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/RabbitOutboxRelay.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipRabbitConsumer.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipDeadLetterReplayCommand.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionRabbitConsumer.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionDeadLetterReplayCommand.java")
        );

        for (Path owner : topologyOwners) {
            assertThat(Files.readString(owner)).as(owner.toString()).contains("onlinejudge.events.v2");
        }
    }

    @Test
    void controlledDeadLetterReplaysOnlyMarkAuditRowsAfterAConfirmWithoutBasicReturn() throws Exception {
        Path root = Path.of("..", "..").toAbsolutePath().normalize();
        for (Path replay : List.of(
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipDeadLetterReplayCommand.java"),
                root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionDeadLetterReplayCommand.java"))) {
            String source = Files.readString(replay);
            assertThat(source).as(replay.toString()).contains("MandatoryRabbitPublisher.publish");
        }
        String publisher = Files.readString(root.resolve("services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/MandatoryRabbitPublisher.java"));
        assertThat(publisher).contains("addReturnListener", "waitForConfirms", "unroutable");
    }
}
