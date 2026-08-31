package com.onlinejudge.lrn.controller;

import com.onlinejudge.lrn.security.LearningServiceIdentityAuthentication;
import com.onlinejudge.lrn.service.LearningTaskPage;
import com.onlinejudge.lrn.service.LearningTaskQuery;
import com.onlinejudge.lrn.service.LearningTaskService;
import com.onlinejudge.lrn.service.LearningTaskSummaryPage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal v2 endpoint for the bounded Course LRN recent-task summary
 * contract (course.openapi.json).  Course has already verified active
 * membership for the requested userId before calling this endpoint; its LRN role
 * only returns deadline-ordered task facts for that member, capped at five.
 */
@RestController
@RequestMapping("/internal/v2/learning")
public class LearningTaskInternalController {
    private static final int MAX_RECENT_TASKS = 5;

    private final LearningTaskService learningTaskService;
    private final LearningServiceIdentityAuthentication serviceIdentity;

    public LearningTaskInternalController(
            LearningTaskService learningTaskService,
            LearningServiceIdentityAuthentication serviceIdentity
    ) {
        this.learningTaskService = learningTaskService;
        this.serviceIdentity = serviceIdentity;
    }

    @GetMapping("/tasks/recent")
    public LearningTaskSummaryPage recentTasks(
            HttpServletRequest request,
            @RequestParam long courseId,
            @RequestParam long userId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        requireRequestId(request);
        serviceIdentity.requireTasksRead(request);
        int boundedLimit = Math.max(1, Math.min(limit, MAX_RECENT_TASKS));
        LearningTaskPage page = learningTaskService.listTasks(
                userId,
                new LearningTaskQuery(null, null, courseId, "deadline", "asc", 1, boundedLimit)
        );
        // v2 internal lists are 0-based; the LRN pagination is
        // 1-based, so the bounded summary page exposes page 0.
        return new LearningTaskSummaryPage(page.records(), page.total(), page.page() - 1, page.size());
    }

    /**
     * course.openapi.json marks X-Request-Id required and UUID-formatted at
     * this receiving boundary; the caller already sends it, so the server-side
     * contract must not silently accept its absence or a malformed value.
     */
    private static void requireRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            throw new InternalV2RequestException(
                    "REQUEST_ID_REQUIRED", "X-Request-Id is required", org.springframework.http.HttpStatus.BAD_REQUEST, false);
        }
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException malformed) {
            throw new InternalV2RequestException(
                    "REQUEST_ID_INVALID", "X-Request-Id must be a UUID", org.springframework.http.HttpStatus.BAD_REQUEST, false);
        }
    }
}
