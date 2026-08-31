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

/**
 * Internal v2 endpoint for the bounded Course -> LRN recent-task summary
 * contract (learning.openapi.json).  Course has already verified active
 * membership for the requested userId before calling this endpoint; Learning
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
        serviceIdentity.requireTasksRead(request);
        int boundedLimit = Math.max(1, Math.min(limit, MAX_RECENT_TASKS));
        LearningTaskPage page = learningTaskService.listTasks(
                userId,
                new LearningTaskQuery(null, null, courseId, "deadline", "asc", 1, boundedLimit)
        );
        // v2 internal lists are 0-based; the Learning service pagination is
        // 1-based, so the bounded summary page exposes page 0.
        return new LearningTaskSummaryPage(page.records(), page.total(), page.page() - 1, page.size());
    }
}
