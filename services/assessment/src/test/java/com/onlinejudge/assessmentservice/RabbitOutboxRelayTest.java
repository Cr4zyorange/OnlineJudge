package com.onlinejudge.assessmentservice;

import com.onlinejudge.assessmentservice.messaging.RabbitOutboxRelay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitOutboxRelayTest {
    @Test
    void relayExposesConfirmBeforeMarkingAnOutboxRecordDelivered() {
        assertThat(RabbitOutboxRelay.PERSISTENT_DELIVERY_MODE).isEqualTo(2);
    }
}
