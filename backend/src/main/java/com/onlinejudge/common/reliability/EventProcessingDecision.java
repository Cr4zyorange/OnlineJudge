package com.onlinejudge.common.reliability;

/** The Rabbit listener maps these outcomes to a manual basicAck/basicNack. */
public enum EventProcessingDecision {
    ACK,
    RETRY,
    DEAD_LETTER
}
