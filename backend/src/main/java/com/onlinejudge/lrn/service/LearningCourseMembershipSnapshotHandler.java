package com.onlinejudge.lrn.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinejudge.common.reliability.NonRetryableEventException;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies Course's atomic roster bootstrap fact.  Per-member events are
 * incremental only; this course aggregate is the durable proof that a roster
 * is complete and safe to use for homework receiver resolution.
 */
@Component
public class LearningCourseMembershipSnapshotHandler {
    public static final String EVENT_TYPE = "course.membership.snapshot.v2";
    public static final String AGGREGATE_TYPE = "course-membership-roster";

    private final LearningCourseMemberProjectionRepository courseMembers;

    public LearningCourseMembershipSnapshotHandler(LearningCourseMemberProjectionRepository courseMembers) {
        this.courseMembers = courseMembers;
    }

    public void apply(ReliableEventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !AGGREGATE_TYPE.equals(envelope.aggregateType())) {
            throw new NonRetryableEventException("Learning roster handler received an unsupported event");
        }
        long courseId = positiveId("courseId", text(envelope.payload(), "courseId"));
        long rosterVersion = positiveId("rosterVersion", text(envelope.payload(), "rosterVersion"));
        if (rosterVersion != envelope.aggregateVersion()) {
            throw new NonRetryableEventException("rosterVersion must equal aggregateVersion");
        }
        if (!String.valueOf(courseId).equals(envelope.aggregateId())) {
            throw new NonRetryableEventException("course membership snapshot aggregateId does not match payload");
        }
        JsonNode membersNode = envelope.payload().path("members");
        if (!membersNode.isArray()) {
            throw new NonRetryableEventException("course membership snapshot members must be an array");
        }
        Set<Long> seenUsers = new HashSet<>();
        List<LearningCourseMemberProjectionRepository.MemberSnapshot> members = new ArrayList<>();
        for (JsonNode member : membersNode) {
            long userId = positiveId("members.userId", text(member, "userId"));
            if (!seenUsers.add(userId)) {
                throw new NonRetryableEventException("course membership snapshot contains duplicate userId");
            }
            String membershipStatus = text(member, "membershipStatus");
            if (!"ACTIVE".equals(membershipStatus) && !"REMOVED".equals(membershipStatus)) {
                throw new NonRetryableEventException("snapshot membershipStatus must be ACTIVE or REMOVED");
            }
            long memberVersion = positiveId("members.memberVersion", text(member, "memberVersion"));
            members.add(new LearningCourseMemberProjectionRepository.MemberSnapshot(userId, membershipStatus, memberVersion));
        }
        courseMembers.replaceWithCompleteRoster(courseId, rosterVersion, members, Instant.now());
    }

    private String text(JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new NonRetryableEventException("course membership snapshot is missing " + field);
        }
        return payload.get(field).asText().trim();
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
