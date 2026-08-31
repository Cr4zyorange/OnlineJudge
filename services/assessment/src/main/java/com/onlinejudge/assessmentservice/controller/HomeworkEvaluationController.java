package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** API-HWK-11 is a passive query; only AssessmentWorker advances the queue. */
@RestController
@RequestMapping("/api/v1/submissions")
public class HomeworkEvaluationController {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;
    private final CoursePermissionClient coursePermissions;

    public HomeworkEvaluationController(JdbcTemplate jdbc, EvaluationTaskRepository tasks,
            CoursePermissionClient coursePermissions) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.coursePermissions = coursePermissions;
    }

    @GetMapping("/{submissionId}/evaluation")
    public Map<String, Object> result(@PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        SubmissionResult result = jdbc.query("""
                SELECT hs.homework_id, hs.student_id, hs.evaluation_status, hs.auto_score, hs.final_score, h.course_id
                  FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.submission_id = ?
                """, (rs, ignored) -> new SubmissionResult(rs.getLong("homework_id"), rs.getString("student_id"),
                rs.getString("evaluation_status"), rs.getBigDecimal("auto_score"), rs.getBigDecimal("final_score"),
                rs.getString("course_id")), submissionId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found"));
        boolean owner = result.studentId().equals(user.id());
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        boolean manager = managerRole && coursePermissions.canManageCourse(result.courseId(), user.id());
        if (!owner && !manager) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "submission result is not visible");
        EvaluationTask task = tasks.findBySubmission(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submissionId", submissionId);
        response.put("homeworkId", result.homeworkId());
        response.put("taskId", task.id());
        response.put("taskState", task.state().name());
        response.put("generation", task.generation());
        response.put("evaluationStatus", result.evaluationStatus());
        response.put("score", result.autoScore());
        response.put("finalScore", result.finalScore());
        return response;
    }

    @PostMapping("/{submissionId}/reevaluate")
    public Map<String, Object> reevaluate(@PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        try {
            java.util.UUID.fromString(requestId);
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id must be a UUID", invalid);
        }
        EvaluationTask task = tasks.findBySubmission(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        boolean canonicalHomeworkTask = "HWK".equals(task.sourceType()) && jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.submission_id = ? AND h.type = 'CODE'
                """, Integer.class, submissionId) == 1;
        if (!canonicalHomeworkTask) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework evaluation not found");
        }
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        if (!managerRole || !coursePermissions.canManageCourse(task.courseId(), user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        if (!tasks.manualReplayHomework(task.id(), user.id(), java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a terminal homework evaluation can be replayed");
        }
        EvaluationTask replayed = tasks.find(task.id()).orElseThrow();
        return Map.of("submissionId", submissionId, "taskId", replayed.id(), "taskState", replayed.state().name(),
                "generation", replayed.generation(), "requestId", requestId);
    }

    private record SubmissionResult(long homeworkId, String studentId, String evaluationStatus,
                                    BigDecimal autoScore, BigDecimal finalScore, String courseId) { }
}
