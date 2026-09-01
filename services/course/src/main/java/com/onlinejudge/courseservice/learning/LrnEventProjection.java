package com.onlinejudge.courseservice.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.persistence.CourseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies #306 frozen facts to Course-owned LRN projections.  The eventId is
 * recorded in the durable inbox in the same transaction as the projection, so
 * a crash or duplicate broker delivery never applies a fact twice.  Receiver
 * resolution is gated by the complete-roster watermark: before it exists, a
 * task/notification fact is recorded as an open reconciliation gap and never
 * fabricates a member conclusion.
 */
@Component
public class LrnEventProjection {
    private final ObjectMapper mapper;
    private final LrnEventInboxRepository inbox;
    private final LrnTaskService tasks;
    private final LrnNotificationService notifications;
    private final CourseRepository courses;

    public LrnEventProjection(ObjectMapper mapper, LrnEventInboxRepository inbox,
                              LrnTaskService tasks, LrnNotificationService notifications,
                              CourseRepository courses) {
        this.mapper = mapper;
        this.inbox = inbox;
        this.tasks = tasks;
        this.notifications = notifications;
        this.courses = courses;
    }

    @Transactional
    public void consume(String body) {
        try {
            JsonNode envelope = mapper.readTree(body);
            String eventId = envelope.path("eventId").asText();
            String eventType = envelope.path("eventType").asText();
            if (eventId.isBlank() || eventType.isBlank()) {
                throw new IllegalArgumentException("envelope must carry eventId and eventType");
            }
            String aggregateType = envelope.path("aggregateType").asText("");
            String aggregateId = envelope.path("aggregateId").asText("");
            long aggregateVersion = envelope.path("aggregateVersion").asLong(0);
            String correlationId = envelope.path("correlationId").asText("");
            if (!inbox.record(eventId, eventType, aggregateType, aggregateId, aggregateVersion, correlationId)) {
                return;
            }
            apply(eventId, eventType, envelope.path("payload"), correlationId);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("malformed v2 envelope", malformed);
        }
    }

    private void apply(String eventId, String eventType, JsonNode payload, String correlationId) {
        switch (eventType) {
            case "course.member.changed.v2" -> memberChanged(payload);
            case "course.membership.snapshot.v2" -> membershipSnapshot(payload);
            case "course.announcement.published.v2" -> announcementPublished(eventId, payload);
            case "assessment.homework.published.v2" ->
                    publishTask(eventId, "HWK", "HOMEWORK", "TASK", "homeworkId", payload, correlationId);
            case "assessment.lab.published.v2" ->
                    publishTask(eventId, "LAB", "EXPERIMENT", "TASK", "labId", payload, correlationId);
            case "assessment.evaluation.completed.v2" ->
                    notifyCourse(eventId, "TASK", "HWK", payload, "评测完成", correlationId);
            case "grade.published.v2" ->
                    notifyCourse(eventId, "GRADE", "GRD", payload, "成绩已发布", correlationId);
            case "grade.review.processed.v2" -> notifyStudent(eventId, "GRADE", "GRD", payload);
            default -> {
                // Retained in the inbox (idempotency) but no LRN projection is needed.
            }
        }
    }

    private void memberChanged(JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long userId = parseId(payload.path("userId").asText());
        String status = payload.path("membershipStatus").asText("");
        long memberVersion = payload.path("memberVersion").asLong(0);
        if (courseId > 0 && userId > 0 && !status.isBlank() && memberVersion > 0) {
            inbox.upsertMember(courseId, userId, status, memberVersion);
        }
    }

    private void membershipSnapshot(JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long version = payload.path("rosterVersion").asLong(0);
        if (courseId <= 0 || version <= 0) return;
        // The watermark only advances: a stale replayed snapshot must never
        // downgrade the roster that receiver resolution depends on.
        if (inbox.watermarkVersion(courseId).map(current -> current >= version).orElse(false)) return;
        List<LrnEventInboxRepository.MemberRow> members = new ArrayList<>();
        for (JsonNode member : payload.path("members")) {
            long userId = parseId(member.path("userId").asText());
            String status = member.path("membershipStatus").asText("");
            long memberVersion = member.path("memberVersion").asLong(0);
            if (userId > 0 && !status.isBlank()) {
                members.add(new LrnEventInboxRepository.MemberRow(userId, status, memberVersion));
            }
        }
        inbox.replaceRoster(courseId, members);
        inbox.recordWatermark(courseId, version);
    }

    private void announcementPublished(String eventId, JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long announcementId = parseId(payload.path("announcementId").asText());
        if (courseId <= 0 || announcementId <= 0) return;
        String title = "课程公告";
        String content = "课程发布了新公告";
        CourseRepository.Announcement announcement = courses.announcement(courseId, announcementId).orElse(null);
        if (announcement != null) {
            title = announcement.title();
            content = announcement.content();
        }
        List<Long> receivers = inbox.activeMemberUserIds(courseId);
        notifications.createForFact(eventId, "course.announcement.published.v2", "TEACHER_ANNOUNCEMENT", courseId,
                "CRS", announcementId, receivers, title, content, 1, "/courses/" + courseId);
    }

    private void publishTask(String eventId, String sourceModule, String taskType, String notificationType,
                             String idField, JsonNode payload, String correlationId) {
        long courseId = parseId(payload.path("courseId").asText());
        long sourceId = parseId(payload.path(idField).asText());
        if (courseId <= 0 || sourceId <= 0) return;
        if (inbox.watermarkVersion(courseId).isEmpty()) {
            inbox.recordGap(courseId, 0, eventId, correlationId);
            return;
        }
        String title = payload.path("title").asText("").trim();
        LocalDateTime deadline = parseTime(payload.path("deadline").asText(null));
        String actionUrl = "/learning/tasks";
        List<Long> receivers = inbox.activeMemberUserIds(courseId);
        tasks.applyPublishedFact(courseId, sourceModule, taskType, title, deadline, actionUrl, sourceId, receivers);
        String content = "新的" + ("HWK".equals(sourceModule) ? "作业" : "实验") + "已发布：" + (title.isEmpty() ? "查看详情" : title);
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule,
                sourceId, receivers, title.isEmpty() ? content : title, content, 1, actionUrl);
    }

    private void notifyCourse(String eventId, String notificationType, String sourceModule, JsonNode payload,
                              String defaultTitle, String correlationId) {
        long courseId = parseId(payload.path("courseId").asText());
        if (courseId <= 0) return;
        if (inbox.watermarkVersion(courseId).isEmpty()) {
            inbox.recordGap(courseId, 0, eventId, correlationId);
            return;
        }
        List<Long> receivers = inbox.activeMemberUserIds(courseId);
        if (receivers.isEmpty()) return;
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule, null,
                receivers, defaultTitle, defaultTitle + "，请前往对应课程查看。", 1, "/learning/tasks");
    }

    private void notifyStudent(String eventId, String notificationType, String sourceModule, JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long studentId = parseId(payload.path("studentId").asText());
        if (courseId <= 0 || studentId <= 0) return;
        String reviewStatus = payload.path("reviewStatus").asText("PROCESSED");
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule, null,
                List.of(studentId), "成绩复核结果", "您的成绩复核申请已处理（" + reviewStatus + "）。", 1, "/learning/tasks");
    }

    private String eventTypeOf(String sourceModule) {
        return switch (sourceModule) {
            case "LAB" -> "assessment.lab.published.v2";
            case "GRD" -> "grade.published.v2";
            default -> "assessment.homework.published.v2";
        };
    }

    private long parseId(String value) {
        if (value == null) return 0;
        String trimmed = value.trim();
        int dash = trimmed.lastIndexOf('-');
        if (dash >= 0 && dash < trimmed.length() - 1) trimmed = trimmed.substring(dash + 1);
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception invalid) {
            return null;
        }
    }
}
