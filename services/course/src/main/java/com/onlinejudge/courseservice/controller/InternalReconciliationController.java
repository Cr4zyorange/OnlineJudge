package com.onlinejudge.courseservice.controller;

import com.onlinejudge.courseservice.learning.LrnEventInboxRepository;
import com.onlinejudge.courseservice.web.CourseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable Course-owned reconciliation request entry (course.openapi.json):
 * source business facts are never rolled back when this endpoint is busy.
 */
@RestController
@RequestMapping("/internal/v2/notifications")
public class InternalReconciliationController {
    private static final List<String> SOURCE_SERVICES = List.of("course", "assessment", "grade");
    private static final List<String> REASONS = List.of("DLQ", "PROJECTION_GAP", "OPERATOR_REPLAY");

    private final LrnEventInboxRepository inbox;

    public InternalReconciliationController(LrnEventInboxRepository inbox) { this.inbox = inbox; }

    @PostMapping("/reconciliation-requests")
    public ResponseEntity<ReconciliationAccepted> request(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ReconciliationRequest body) {
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "RECONCILIATION_INVALID",
                    "Idempotency-Key must be 16-128 characters", false);
        }
        if (body == null || body.sourceService() == null || body.eventId() == null || body.reason() == null
                || !SOURCE_SERVICES.contains(body.sourceService()) || !REASONS.contains(body.reason())) {
            throw new CourseException(HttpStatus.BAD_REQUEST, "RECONCILIATION_INVALID",
                    "reconciliation request payload is invalid", false);
        }
        String requestId = UUID.randomUUID().toString();
        Optional<String> accepted = inbox.requestReconciliation(body.sourceService(), body.eventId(), body.reason(), requestId);
        if (accepted.isEmpty()) {
            throw new CourseException(HttpStatus.CONFLICT, "RECONCILIATION_IDEMPOTENCY_CONFLICT",
                    "reconciliation request already exists for this source event", false);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ReconciliationAccepted(accepted.get(), "ACCEPTED"));
    }

    public record ReconciliationRequest(String sourceService, String eventId, String reason) { }
    public record ReconciliationAccepted(String requestId, String status) { }
}
