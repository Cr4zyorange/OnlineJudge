package com.onlinejudge.courseservice.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.persistence.CourseRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * task/notification fact is durably deferred (the original envelope is
 * persisted in the same transaction), recorded as an open reconciliation gap,
 * and replayed exactly once by its original eventId after the authoritative
 * roster snapshot catches up.
 */
@Component
public class LrnEventProjection {
    private static final Logger log = LoggerFactory.getLogger(LrnEventProjection.class);
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
            apply(eventId, eventType, envelope, correlationId, body);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("malformed v2 envelope", malformed);
        }
    }

    private void apply(String eventId, String eventType, JsonNode envelope, String correlationId, String envelopeJson) {
        JsonNode payload = envelope.path("payload");
        switch (eventType) {
            case "course.member.changed.v2" -> memberChanged(eventId, payload, correlationId);
            case "course.membership.snapshot.v2" -> membershipSnapshot(payload);
            case "course.announcement.published.v2" -> announcementPublished(eventId, payload);
            case "assessment.source-grade.changed.v2" -> sourceGradePublished(eventId, payload);
            case "assessment.homework.published.v2" ->
                    publishTask(eventId, "HWK", "HOMEWORK", "TASK", "homeworkId", envelope, correlationId, envelopeJson);
            case "assessment.lab.published.v2" ->
                    publishTask(eventId, "LAB", "EXPERIMENT", "TASK", "labId", envelope, correlationId, envelopeJson);
            case "assessment.evaluation.completed.v2" ->
                    notifyCourse(eventId, "TASK", "HWK", null, envelope, "评测完成", correlationId, envelopeJson);
            case "grade.published.v2" ->
                    notifyCourse(eventId, "GRADE", "GRD", "publicationId", envelope, "成绩已发布", correlationId, envelopeJson);
            case "grade.review.processed.v2" ->
                    notifyStudent(eventId, "GRADE", "GRD", "reviewRequestId", payload);
            default -> {
                // Retained in the inbox (idempotency) but no LRN projection is needed.
            }
        }
    }

    private void memberChanged(String eventId, JsonNode payload, String correlationId) {
        long courseId = parseId(payload.path("courseId").asText());
        long userId = parseId(payload.path("userId").asText());
        String status = payload.path("membershipStatus").asText("");
        long memberVersion = payload.path("memberVersion").asLong(0);
        if (courseId > 0 && userId > 0 && !status.isBlank() && memberVersion > 0) {
            inbox.upsertMember(courseId, userId, status, memberVersion, eventId, correlationId);
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
        for (LrnEventInboxRepository.MemberRow member : members) {
            inbox.resolveMemberGap(courseId, member.userId(), member.memberVersion());
        }
        replayDeferredFacts(courseId);
    }

    /**
     * Once the complete roster has replaced the projection, replay every
     * pending deferred fact of that course exactly once by its original
     * eventId and close the roster gap.  The inbox already carries the event,
     * so replay applies the projection directly; task and notification
     * creation are idempotent per eventId.
     */
    private void replayDeferredFacts(long courseId) {
        boolean replayedAny = false;
        for (LrnEventInboxRepository.DeferredEvent deferred : inbox.pendingDeferredEvents()) {
            JsonNode envelope;
            try {
                envelope = mapper.readTree(deferred.envelopeJson());
            } catch (JsonProcessingException malformed) {
                continue;
            }
            if (parseId(envelope.path("payload").path("courseId").asText()) != courseId) continue;
            String eventId = envelope.path("eventId").asText("");
            String eventType = envelope.path("eventType").asText("");
            apply(eventId, eventType, envelope, deferred.correlationId(), deferred.envelopeJson());
            replayedAny |= inbox.markDeferredResolved(eventId);
        }
        if (replayedAny) {
            inbox.resolveRosterGap(courseId);
        }
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

    /**
     * A SCORED source-grade fact is the existing cross-service completion fact
     * for a student-visible LAB/HWK score.  Keep recipient resolution tied to
     * the event's student rather than broadening it to the entire course.
     */
    private void sourceGradePublished(String eventId, JsonNode payload) {
        String sourceModule = payload.path("sourceType").asText("");
        if (!"SCORED".equals(payload.path("status").asText())
                || !("HWK".equals(sourceModule) || "LAB".equals(sourceModule))) {
            return;
        }
        long courseId = parseId(payload.path("courseId").asText());
        long sourceId = parseId(payload.path("sourceId").asText());
        long studentId = parseId(payload.path("studentId").asText());
        if (courseId <= 0 || sourceId <= 0 || studentId <= 0) return;
        String title = "HWK".equals(sourceModule) ? "homework score published" : "实验成绩已发布";
        String content = "HWK".equals(sourceModule) ? "作业成绩已发布，请查看反馈。" : "实验成绩已发布，请查看反馈。";
        notifications.createForFact(eventId, "assessment.source-grade.changed.v2", "GRADE", courseId,
                sourceModule, sourceId, List.of(studentId), title, content, 1,
                taskActionUrl(sourceModule, courseId, sourceId));
    }

    private void publishTask(String eventId, String sourceModule, String taskType, String notificationType,
                             String idField, JsonNode envelope, String correlationId, String envelopeJson) {
        JsonNode payload = envelope.path("payload");
        long courseId = parseId(payload.path("courseId").asText());
        long sourceId = parseId(payload.path(idField).asText());
        if (courseId <= 0 || sourceId <= 0) return;
        if (inbox.watermarkVersion(courseId).isEmpty()) {
            deferAndRecordGap(courseId, eventId, envelope, correlationId, envelopeJson);
            return;
        }
        String title = payload.path("title").asText("").trim();
        LocalDateTime deadline = parseTime(payload.path("deadline").asText(null));
        String actionUrl = taskActionUrl(sourceModule, courseId, sourceId);
        List<Long> receivers = inbox.activeMemberUserIds(courseId);
        if (receivers.isEmpty()) return;
        tasks.applyPublishedFact(courseId, sourceModule, taskType, title, deadline, actionUrl, sourceId, receivers);
        String content = "新的" + ("HWK".equals(sourceModule) ? "作业" : "实验") + "已发布：" + (title.isEmpty() ? "查看详情" : title);
        String notificationTitle = "HWK".equals(sourceModule) ? "homework published" : (title.isEmpty() ? content : title);
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule,
                sourceId, receivers, notificationTitle, content, 1, actionUrl);
    }

    private void notifyCourse(String eventId, String notificationType, String sourceModule, String sourceIdField, JsonNode envelope,
                              String defaultTitle, String correlationId, String envelopeJson) {
        JsonNode payload = envelope.path("payload");
        long courseId = parseId(payload.path("courseId").asText());
        if (courseId <= 0) return;
        if (inbox.watermarkVersion(courseId).isEmpty()) {
            deferAndRecordGap(courseId, eventId, envelope, correlationId, envelopeJson);
            return;
        }
        List<Long> receivers = inbox.activeMemberUserIds(courseId);
        if (receivers.isEmpty()) return;
        long parsedSourceId = sourceIdField == null ? 0 : parseId(payload.path(sourceIdField).asText());
        Long sourceId = parsedSourceId > 0 ? parsedSourceId : null;
        LrnNotificationService.NotificationEventResult result = notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType,
                courseId, sourceModule, sourceId, receivers, defaultTitle, defaultTitle + "，请前往对应课程查看。", 1, "/learning/tasks");
        log.info("lrn_notification_projected eventId={} correlationId={} sourceModule={} sourceId={} notificationIds={}",
                eventId, correlationId, sourceModule, sourceId, result.notificationIds());
    }

    /** Same transaction: keep the original envelope replayable and record the open roster gap. */
    private void deferAndRecordGap(long courseId, String eventId, JsonNode envelope,
                                   String correlationId, String envelopeJson) {
        inbox.deferEvent(eventId, envelope.path("eventType").asText(""),
                envelope.path("aggregateType").asText(""), envelope.path("aggregateId").asText(""),
                envelope.path("aggregateVersion").asLong(0), correlationId, envelopeJson);
        inbox.recordGap(courseId, 0, eventId, correlationId);
    }

    private void notifyStudent(String eventId, String notificationType, String sourceModule,
                               String sourceIdField, JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long studentId = parseId(payload.path("studentId").asText());
        if (courseId <= 0 || studentId <= 0) return;
        String reviewStatus = payload.path("reviewStatus").asText("PROCESSED");
        long parsedSourceId = parseId(payload.path(sourceIdField).asText());
        Long sourceId = parsedSourceId > 0 ? parsedSourceId : null;
        notifications.createForFact(eventId, "grade.review.processed.v2", notificationType, courseId, sourceModule, sourceId,
                List.of(studentId), "成绩复核已处理", "您的成绩复核申请已处理（" + reviewStatus + "）。", 1, "/learning/tasks");
    }

    private String eventTypeOf(String sourceModule) {
        return switch (sourceModule) {
            case "LAB" -> "assessment.lab.published.v2";
            case "GRD" -> "grade.published.v2";
            default -> "assessment.homework.published.v2";
        };
    }

    private String taskActionUrl(String sourceModule, long courseId, long sourceId) {
        return switch (sourceModule) {
            case "LAB" -> "/courses/" + courseId + "/labs/" + sourceId;
            case "HWK" -> "/courses/" + courseId + "/homeworks/" + sourceId;
            default -> "/learning/tasks";
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
