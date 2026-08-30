package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** HTTP result reads are intentionally passive: queue advancement only occurs in AssessmentWorker. */
@RestController
@RequestMapping("/api/v1/evaluations")
public class AssessmentEvaluationController {
    private final EvaluationTaskRepository tasks;
    public AssessmentEvaluationController(EvaluationTaskRepository tasks) { this.tasks = tasks; }

    @GetMapping("/{taskId}")
    public Map<String, Object> get(@PathVariable String taskId) {
        EvaluationTask task = tasks.find(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        return Map.of("taskId", task.id(), "submissionId", task.submissionId(), "state", task.state().name(),
                "generation", task.generation(), "resultStatus", task.resultStatus() == null ? "" : task.resultStatus());
    }
}
