package com.onlinejudge.assessmentservice.service;

/** Only the local Homework/outbox transaction maps to 503/HWK_5003. */
public class HomeworkPublicationException extends RuntimeException {
    public HomeworkPublicationException(Throwable cause) {
        super("homework publication transaction failed", cause);
    }
}
