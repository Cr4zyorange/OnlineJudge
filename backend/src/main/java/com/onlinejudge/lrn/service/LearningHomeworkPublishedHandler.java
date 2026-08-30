package com.onlinejudge.lrn.service;

import com.onlinejudge.common.reliability.NonRetryableEventException;
import com.onlinejudge.common.reliability.ReliableEventEnvelope;
import com.onlinejudge.lrn.repository.JdbcNotificationRepository;
import com.onlinejudge.lrn.repository.LearningCourseMemberProjectionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Applies the Assessment homework event using only Learning-owned projections. */
@Component
public class LearningHomeworkPublishedHandler {
    private final LearningCourseMemberProjectionRepository courseMembers;
    private final JdbcNotificationRepository notifications;

    public LearningHomeworkPublishedHandler(
            LearningCourseMemberProjectionRepository courseMembers,
            JdbcNotificationRepository notifications
    ) {
        this.courseMembers = courseMembers;
        this.notifications = notifications;
    }

    public void apply(ReliableEventEnvelope envelope) {
        if (!"assessment.homework.published.v2".equals(envelope.eventType())) {
            throw new NonRetryableEventException("Learning homework handler received unsupported event type");
        }
        var payload = envelope.payload();
        if (payload.has("recipientUserIds") || payload.has("students") || payload.has("roster")) {
            throw new NonRetryableEventException("homework event must not carry a recipient roster");
        }
        String receiverScope = text(payload, "receiverScope");
        if (!"COURSE_ACTIVE_STUDENTS".equals(receiverScope)) {
            throw new NonRetryableEventException("unsupported homework receiverScope");
        }
        long courseId = positiveId(payload, "courseId");
        long homeworkId = positiveId(payload, "homeworkId");
        String title = text(payload, "title");
        if (title.length() > 100) {
            throw new NonRetryableEventException("homework title exceeds v2 envelope bound");
        }
        String deadline = requiredRfc3339(payload, "deadline");
        requiredRfc3339(payload, "publishedAt");

        List<Long> recipients = courseMembers.activeStudentIds(courseId);
        if (recipients.isEmpty() && !courseMembers.hasObservedCourse(courseId)) {
            throw new CourseProjectionUnavailableException(
                    "Learning has not yet received course.member.changed.v2 for course " + courseId);
        }
        for (Long recipient : recipients) {
            NotificationCreateCommand command = new NotificationCreateCommand(
                    "homework:" + homeworkId + ":" + recipient,
                    envelope.eventType(),
                    "TASK",
                    courseId,
                    "HWK",
                    homeworkId,
                    List.of(recipient),
                    "homework published",
                    "New homework: " + title + " (deadline " + deadline + ")",
                    1,
                    "/courses/" + courseId + "/homeworks/" + homeworkId
            );
            notifications.save(recipient, "TASK", command, command.idempotencyKey());
        }
    }

    private long positiveId(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        String value = text(payload, field);
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

    private String text(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        if (!payload.hasNonNull(field) || payload.get(field).asText().isBlank()) {
            throw new NonRetryableEventException("homework event is missing " + field);
        }
        return payload.get(field).asText().trim();
    }

    private String requiredRfc3339(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        String value = text(payload, field);
        try {
            Instant.parse(value);
            return value;
        } catch (RuntimeException exception) {
            throw new NonRetryableEventException(field + " must be RFC3339");
        }
    }
}
