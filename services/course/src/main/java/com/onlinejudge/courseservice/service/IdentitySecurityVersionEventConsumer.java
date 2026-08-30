package com.onlinejudge.courseservice.service;

import com.onlinejudge.courseservice.persistence.CourseEventInboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Course-owned reliable consumer for Identity's canonical security-version event. */
@Component
public class IdentitySecurityVersionEventConsumer {
    private final CourseEventInboxRepository inbox;

    public IdentitySecurityVersionEventConsumer(CourseEventInboxRepository inbox) {
        this.inbox = inbox;
    }

    @Transactional
    public CourseEventInboxRepository.ProjectionResult consume(String rawEnvelope) {
        return inbox.projectIdentitySecurityVersion(rawEnvelope);
    }
}
