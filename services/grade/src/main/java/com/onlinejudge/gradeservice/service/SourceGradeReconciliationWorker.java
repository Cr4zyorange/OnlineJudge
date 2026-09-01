package com.onlinejudge.gradeservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.gradeservice.messaging.SourceGradeChangedEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Uses Assessment's frozen stable-page endpoint only for rebuild/reconciliation, never normal calculation. */
@Component
public class SourceGradeReconciliationWorker {
    private final JdbcTemplate jdbc;
    private final SourceGradeProjectionService projection;
    private final ObjectMapper json;
    private final String baseUrl, serviceAuthorization;
    private final Duration timeout;
    private final HttpClient http;

    public SourceGradeReconciliationWorker(JdbcTemplate jdbc, SourceGradeProjectionService projection, ObjectMapper json,
            @Value("${grade.assessment.base-url:http://127.0.0.1:8083}") String baseUrl,
            @Value("${grade.assessment.service-authorization:}") String serviceAuthorization,
            @Value("${grade.assessment.timeout:PT1S}") Duration timeout) {
        this.jdbc = jdbc; this.projection = projection; this.json = json;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceFirst("/+$", "");
        this.serviceAuthorization = serviceAuthorization; this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Scheduled(fixedDelayString = "${grade.assessment.reconciliation-interval-ms:5000}")
    public int reconcilePending() {
        if (baseUrl.isBlank() || serviceAuthorization == null || serviceAuthorization.isBlank()) return 0;
        List<PendingGap> gaps = jdbc.query("""
                SELECT request.aggregate_id, request.correlation_id,
                       deferred.course_id, deferred.source_type, deferred.source_id, deferred.student_id
                  FROM grade_source_reconciliation_request request
                  JOIN grade_source_deferred_event deferred ON deferred.aggregate_id=request.aggregate_id
                 WHERE request.request_status='PENDING'
                 ORDER BY request.requested_at LIMIT 20
                """, (rs, ignored) -> new PendingGap(rs.getString("aggregate_id"), rs.getString("correlation_id"),
                rs.getString("course_id"), rs.getString("source_type"), rs.getString("source_id"), rs.getString("student_id")));
        int resolved = 0;
        for (PendingGap gap : gaps) {
            try {
                SourceGradeChangedEnvelope fact = fetchStableSnapshot(gap).stream()
                        .filter(item -> item.aggregateId().equals(gap.aggregateId())).findFirst().orElse(null);
                if (fact != null) {
                    projection.reconcileSnapshot(fact);
                    resolved++;
                }
            } catch (Exception unavailableOrExpired) {
                // Preserve PENDING. A later bounded run restarts from a fresh stable snapshot.
            }
        }
        return resolved;
    }

    private List<SourceGradeChangedEnvelope> fetchStableSnapshot(PendingGap gap) throws Exception {
        List<SourceGradeChangedEnvelope> facts = new ArrayList<>();
        long total = Long.MAX_VALUE;
        long snapshotVersion = 0;
        int page = 0;
        while (facts.size() < total) {
            String query = "courseId=" + encode(gap.courseId()) + "&sourceType=" + encode(gap.sourceType())
                    + "&sourceId=" + encode(gap.sourceId()) + "&page=" + page + "&size=100"
                    + (snapshotVersion == 0 ? "" : "&snapshotVersion=" + snapshotVersion);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/v2/source-grades?" + query))
                    .timeout(timeout).header("Accept", "application/json")
                    .header("X-Request-Id", gap.correlationId())
                    .header("X-OnlineJudge-Service-Authorization", serviceAuthorization).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Assessment returned HTTP " + response.statusCode());
            JsonNode root = json.readTree(response.body());
            if (!root.path("items").isArray() || root.path("page").asInt(-1) != page
                    || !root.path("total").isIntegralNumber() || !root.path("sourceSnapshotVersion").isIntegralNumber()) {
                throw new IllegalArgumentException("Assessment source-grade page is malformed");
            }
            long pageSnapshotVersion = root.path("sourceSnapshotVersion").asLong();
            if (pageSnapshotVersion < 1 || (snapshotVersion != 0 && snapshotVersion != pageSnapshotVersion)) {
                throw new IllegalArgumentException("Assessment source-grade snapshot changed between pages");
            }
            snapshotVersion = pageSnapshotVersion;
            total = root.path("total").asLong();
            int before = facts.size();
            for (JsonNode item : root.path("items")) facts.add(toSnapshotFact(item, gap));
            if (facts.size() == before && facts.size() < total) throw new IllegalArgumentException("Assessment page is incomplete");
            page++;
        }
        return facts;
    }

    private SourceGradeChangedEnvelope toSnapshotFact(JsonNode item, PendingGap gap) {
        String courseId = text(item, "courseId"), sourceType = text(item, "sourceType");
        String sourceId = text(item, "sourceId"), studentId = text(item, "studentId");
        String status = text(item, "status");
        if (!courseId.equals(gap.courseId()) || !sourceType.equals(gap.sourceType()) || !sourceId.equals(gap.sourceId())) {
            throw new IllegalArgumentException("Assessment source-grade item escaped its filter");
        }
        BigDecimal fullScore = item.path("fullScore").decimalValue();
        BigDecimal score = "UNGRADED".equals(status) ? null : item.path("score").decimalValue();
        long sourceVersion = item.path("sourceVersion").asLong();
        Instant updatedAt = Instant.parse(text(item, "updatedAt"));
        String aggregateId = sourceType + ":" + sourceId + ":" + studentId;
        String eventId = UUID.nameUUIDFromBytes(("grade-reconcile:" + aggregateId + ":" + sourceVersion)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new SourceGradeChangedEnvelope(eventId, aggregateId, sourceVersion, updatedAt, gap.correlationId(),
                courseId, sourceType, sourceId, studentId, score, fullScore, status, sourceVersion);
    }

    private static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw new IllegalArgumentException("missing " + field);
        return node.path(field).asText();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record PendingGap(String aggregateId, String correlationId, String courseId, String sourceType, String sourceId, String studentId) { }
}
