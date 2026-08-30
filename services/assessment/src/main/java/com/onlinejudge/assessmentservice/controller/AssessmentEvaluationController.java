package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** HTTP result reads are intentionally passive: queue advancement only occurs in AssessmentWorker. */
@RestController
@RequestMapping("/api/v1/evaluations")
public class AssessmentEvaluationController {
    private final EvaluationTaskRepository tasks; private final CourseMemberProjectionRepository courseMembers;
    public AssessmentEvaluationController(EvaluationTaskRepository tasks, CourseMemberProjectionRepository courseMembers) { this.tasks = tasks; this.courseMembers = courseMembers; }

    @GetMapping("/{taskId}")
    public Map<String, Object> get(@PathVariable String taskId, @RequestAttribute("assessment.currentUser") CurrentUser user) {
        EvaluationTask task = tasks.find(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        boolean submitter = task.studentId().equals(user.id());
        boolean courseTeacher = user.hasRole("TEACHER") && courseMembers.isActive(task.courseId(), user.id());
        if (!submitter && !courseTeacher) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "evaluation access is restricted to the submitter or an active course teacher");
        return Map.of("taskId", task.id(), "submissionId", task.submissionId(), "state", task.state().name(),
                "generation", task.generation(), "resultStatus", task.resultStatus() == null ? "" : task.resultStatus());
    }

    @PostMapping("/{taskId}/replay")
    public Map<String, Object> replay(@PathVariable String taskId, @RequestAttribute("assessment.currentUser") CurrentUser user,
            jakarta.servlet.http.HttpServletRequest request) {
        if (request.getHeader("X-Request-Id") == null || request.getHeader("X-Request-Id").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        EvaluationTask task = tasks.find(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        if (!user.hasRole("TEACHER") || !courseMembers.isActive(task.courseId(), user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course teacher membership is required");
        if (!tasks.manualReplay(task.id(), user.id(), java.time.Instant.now())) throw new ResponseStatusException(HttpStatus.CONFLICT, "only a terminal failed evaluation can be replayed");
        EvaluationTask replayed = tasks.find(task.id()).orElseThrow();
        return Map.of("taskId", replayed.id(), "state", replayed.state().name(), "generation", replayed.generation());
    }
}
