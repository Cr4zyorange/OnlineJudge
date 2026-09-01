package com.onlinejudge.courseservice.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Applies #306 frozen facts to Course-owned LRN projections.  The eventId is
 * recorded in the durable inbox in the same transaction as the projection, so
 * a crash or duplicate broker delivery can never apply a fact twice.
 */
@Component
public class LrnEventProjection {
    private final ObjectMapper mapper;
    private final LrnEventInboxRepository inbox;
    private final LrnTaskService tasks;
    private final LrnNotificationService notifications;

    public LrnEventProjection(ObjectMapper mapper, LrnEventInboxRepository inbox,
                              LrnTaskService tasks, LrnNotificationService notifications) {
        this.mapper = mapper;
        this.inbox = inbox;
        this.tasks = tasks;
        this.notifications = notifications;
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
            String payloadJson = envelope.path("payload").toString();
            if (!inbox.record(eventId, eventType, aggregateType, aggregateId, aggregateVersion, payloadJson)) {
                return;
            }
            apply(eventId, eventType, envelope.path("payload"));
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("malformed v2 envelope", malformed);
        }
    }

    private void apply(String eventId, String eventType, JsonNode payload) {
        switch (eventType) {
            case "course.membership.snapshot.v2" -> {
                long courseId = parseId(payload.path("courseId").asText());
                long version = payload.path("rosterVersion").asLong(0);
                if (courseId > 0 && version > 0) inbox.recordWatermark(courseId, version);
            }
            case "assessment.homework.published.v2" ->
                    publishTask(eventId, "HWK", "HOMEWORK", "TASK", "homeworkId", payload);
            case "assessment.lab.published.v2" ->
                    publishTask(eventId, "LAB", "EXPERIMENT", "TASK", "labId", payload);
            case "assessment.evaluation.completed.v2" ->
                    notifyReceivers(eventId, "TASK", "HWK", payload, "评测完成");
            case "grade.published.v2" ->
                    notifyReceivers(eventId, "GRADE", "GRD", payload, "成绩已发布");
            default -> {
                // Other facts are retained in the inbox (idempotency) but need no LRN projection.
            }
        }
    }

    private void publishTask(String eventId, String sourceModule, String taskType, String notificationType,
                             String idField, JsonNode payload) {
        long courseId = parseId(payload.path("courseId").asText());
        long sourceId = parseId(payload.path(idField).asText());
        if (courseId <= 0 || sourceId <= 0) return;
        String title = payload.path("title").asText("").trim();
        LocalDateTime deadline = parseTime(payload.path("deadline").asText(null));
        String actionUrl = "/learning/tasks";
        List<Long> receivers = tasks.applyPublishedFact(courseId, sourceModule, taskType, title, deadline, actionUrl, sourceId);
        String content = "新的" + ("HWK".equals(sourceModule) ? "作业" : "实验") + "已发布：" + (title.isEmpty() ? "查看详情" : title);
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule,
                sourceId, receivers, title.isEmpty() ? content : title, content, 1, actionUrl);
    }

    private void notifyReceivers(String eventId, String notificationType, String sourceModule, JsonNode payload,
                                 String defaultTitle) {
        long courseId = parseId(payload.path("courseId").asText());
        if (courseId <= 0) return;
        String studentText = payload.has("studentId") ? payload.path("studentId").asText()
                : (payload.has("userId") ? payload.path("userId").asText() : "");
        List<Long> receivers = studentText.isBlank() ? tasks.activeStudentIds(courseId)
                : List.of(parseId(studentText));
        receivers = receivers.stream().filter(id -> id > 0).distinct().toList();
        if (receivers.isEmpty()) return;
        String title = payload.path("title").asText("").trim();
        String finalTitle = title.isEmpty() ? defaultTitle : title;
        notifications.createForFact(eventId, eventTypeOf(sourceModule), notificationType, courseId, sourceModule, null,
                receivers, finalTitle, finalTitle + "，请前往对应课程查看。", 1, "/learning/tasks");
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
