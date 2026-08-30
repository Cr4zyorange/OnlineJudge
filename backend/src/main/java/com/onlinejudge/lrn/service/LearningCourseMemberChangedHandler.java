package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.NonRetryableEventException;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Applies the closed course.member.changed.v2 fact to Learning-owned state. */
@Component
public class LearningCourseMemberChangedHandler {
    private final LearningCourseMemberProjectionRepository courseMembers;

    public LearningCourseMemberChangedHandler(LearningCourseMemberProjectionRepository courseMembers) {
        this.courseMembers = courseMembers;
    }

    public void apply(ReliableEventEnvelope envelope) {
        if (!"course.member.changed.v2".equals(envelope.eventType())
                || !"course-member".equals(envelope.aggregateType())) {
            throw new NonRetryableEventException("Learning member handler received an unsupported event");
        }
        long courseId = positiveId("courseId", text(envelope, "courseId"));
        long userId = positiveId("userId", text(envelope, "userId"));
        String membershipStatus = text(envelope, "membershipStatus");
        if (!"ACTIVE".equals(membershipStatus) && !"REMOVED".equals(membershipStatus)) {
            throw new NonRetryableEventException("membershipStatus must be ACTIVE or REMOVED");
        }
        long memberVersion = positiveId("memberVersion", text(envelope, "memberVersion"));
        if (memberVersion != envelope.aggregateVersion()) {
            throw new NonRetryableEventException("memberVersion must equal aggregateVersion");
        }
        if (!(courseId + ":" + userId).equals(envelope.aggregateId())) {
            throw new NonRetryableEventException("course member aggregateId does not match payload");
        }
        courseMembers.upsert(courseId, userId, membershipStatus, memberVersion, Instant.now());
    }

    private String text(ReliableEventEnvelope envelope, String field) {
        if (!envelope.payload().hasNonNull(field) || envelope.payload().get(field).asText().isBlank()) {
            throw new NonRetryableEventException("course member event is missing " + field);
        }
        return envelope.payload().get(field).asText().trim();
    }

    private long positiveId(String field, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException(value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new NonRetryableEventException(field + " must be a positive numeric id in the current gateway adapter");
        }
    }
}
