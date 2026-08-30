package com.onlinejudge.common.reliability;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqReliabilityConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RabbitMqReliabilityConfiguration.class)
            .withBean(CachingConnectionFactory.class, () -> new CachingConnectionFactory("localhost"));

    @Test
    void declaresDurableMainRetryAndDeadLetterTopologyWithoutMakingRetryAnImmediateLoop() {
        contextRunner.withPropertyValues("onlinejudge.reliability.rabbitmq.enabled=true")
                .run(context -> {
                    Queue main = context.getBean("learningEventsQueue", Queue.class);
                    Queue retry = context.getBean("learningRetryQueue", Queue.class);
                    Queue rosterRetry = context.getBean("learningCourseRosterRetryQueue", Queue.class);
                    Queue deadLetter = context.getBean("learningDeadLetterQueue", Queue.class);

                    assertThat(main.isDurable()).isTrue();
                    assertThat(main.getArguments()).containsEntry("x-dead-letter-exchange", "onlinejudge.events.dlx.v2");
                    assertThat(retry.isDurable()).isTrue();
                    assertThat(retry.getArguments())
                            .containsEntry("x-message-ttl", 1_000)
                            .containsEntry("x-dead-letter-exchange", "onlinejudge.events.v2")
                            .containsEntry("x-dead-letter-routing-key", "onlinejudge.assessment.homework.published.v2");
                    assertThat(deadLetter.isDurable()).isTrue();
                    assertThat(context.getBean("courseMemberChangedToLearning", Binding.class).getRoutingKey())
                            .isEqualTo("onlinejudge.course.member.changed.v2");
                    assertThat(rosterRetry.getArguments())
                            .containsEntry("x-message-ttl", 1_000)
                            .containsEntry("x-dead-letter-routing-key", "onlinejudge.course.membership.snapshot.v2");
                    assertThat(context.getBean("courseMembershipSnapshotToLearning", Binding.class).getRoutingKey())
                            .isEqualTo("onlinejudge.course.membership.snapshot.v2");
                });
    }
}
