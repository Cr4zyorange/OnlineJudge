package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.HomeworkEvaluationReplayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API-HWK-11 is a passive query; only AssessmentWorker advances the queue. */
@RestController
@RequestMapping("/api/v1/submissions")
public class HomeworkEvaluationController {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;
    private final CoursePermissionClient coursePermissions;
    private final HomeworkEvaluationReplayService replayService;

    public HomeworkEvaluationController(JdbcTemplate jdbc, EvaluationTaskRepository tasks,
            CoursePermissionClient coursePermissions, HomeworkEvaluationReplayService replayService) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.coursePermissions = coursePermissions;
        this.replayService = replayService;
    }

    @GetMapping("/{submissionId}/evaluation")
    public Map<String, Object> result(@PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        SubmissionResult result = resolveSubmission(submissionId);
        boolean owner = result.studentId().equals(user.id());
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        boolean manager = !owner && managerRole && coursePermissions.canManageCourse(result.courseId(), user.id());
        if (!owner && !manager) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "submission result is not visible");
        EvaluationTask task = tasks.findBySubmission(result.internalSubmissionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        Map<String, Object> data = resultData(result, task);
        data.put("evaluationHistory", jdbc.query("""
                SELECT id, evaluation_type, status, score, started_at, finished_at
                  FROM assessment_homework_evaluation
                 WHERE submission_id = ?
                 ORDER BY finished_at DESC, id DESC
                """, (rs, ignored) -> historyItem(rs), result.internalSubmissionId()));
        return success(data);
    }

    @PostMapping("/{submissionId}/reevaluate")
    public Map<String, Object> reevaluate(@PathVariable String submissionId,
            @Valid @RequestBody ReevaluateHomeworkRequest requestBody,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest request) {
        String requestId = requestId(request);
        SubmissionResult submission = resolveSubmission(submissionId);
        EvaluationTask task = tasks.findBySubmission(submission.internalSubmissionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation not found"));
        boolean canonicalHomeworkTask = "HWK".equals(task.sourceType()) && jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.submission_id = ? AND h.type = 'CODE'
                """, Integer.class, submission.internalSubmissionId()) == 1;
        if (!canonicalHomeworkTask) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "homework evaluation not found");
        }
        boolean managerRole = user.hasRole("TEACHER") || user.hasRole("ASSISTANT");
        if (!managerRole || !coursePermissions.canManageCourse(task.courseId(), user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        try {
            if (!replayService.replay(task, user.id(), requestBody.reason().trim(), requestId, java.time.Instant.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "only a terminal homework evaluation can be replayed");
            }
        } catch (HomeworkEvaluationReplayService.HomeworkScoresPublishedException published) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, published.getMessage(), published);
        }
        EvaluationTask replayed = tasks.find(task.id()).orElseThrow();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", submission.publicSubmissionId());
        data.put("taskId", replayed.id());
        data.put("taskState", replayed.state().name());
        data.put("generation", replayed.generation());
        data.put("requestId", requestId);
        return success(data);
    }

    private SubmissionResult resolveSubmission(String externalSubmissionId) {
        List<SubmissionResult> rows = jdbc.query("""
                SELECT hs.submission_id, hs.public_id, hs.homework_id, hs.student_id, hs.evaluation_status, hs.auto_score, hs.final_score, h.course_id
                  FROM assessment_homework_submission hs
                  JOIN assessment_homework h ON h.id = hs.homework_id
                 WHERE hs.submission_id = ?
                """, (rs, ignored) -> new SubmissionResult(rs.getString("submission_id"), rs.getLong("public_id"),
                rs.getLong("homework_id"), rs.getString("student_id"),
                rs.getString("evaluation_status"), rs.getBigDecimal("auto_score"), rs.getBigDecimal("final_score"),
                rs.getString("course_id")), externalSubmissionId);
        if (!rows.isEmpty()) return rows.getFirst();
        try {
            long publicId = Long.parseLong(externalSubmissionId);
            return jdbc.query("""
                    SELECT hs.submission_id, hs.public_id, hs.homework_id, hs.student_id, hs.evaluation_status, hs.auto_score, hs.final_score, h.course_id
                      FROM assessment_homework_submission hs
                      JOIN assessment_homework h ON h.id = hs.homework_id
                     WHERE hs.public_id = ?
                    """, (rs, ignored) -> new SubmissionResult(rs.getString("submission_id"), rs.getLong("public_id"),
                    rs.getLong("homework_id"), rs.getString("student_id"), rs.getString("evaluation_status"),
                    rs.getBigDecimal("auto_score"), rs.getBigDecimal("final_score"), rs.getString("course_id")), publicId)
                    .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found"));
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found", invalid);
        }
    }

    private static Map<String, Object> resultData(SubmissionResult result, EvaluationTask task) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submissionId", result.publicSubmissionId());
        response.put("homeworkId", result.homeworkId());
        response.put("taskId", task.id());
        response.put("taskState", task.state().name());
        response.put("generation", task.generation());
        response.put("evaluationStatus", result.evaluationStatus());
        response.put("score", result.autoScore());
        response.put("finalScore", result.finalScore());
        return response;
    }

    private static Map<String, Object> historyItem(java.sql.ResultSet row) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evaluationId", row.getLong("id"));
        item.put("evaluationType", row.getString("evaluation_type"));
        item.put("status", row.getString("status"));
        item.put("score", row.getBigDecimal("score"));
        item.put("startedAt", row.getTimestamp("started_at").toInstant().toString());
        item.put("finishedAt", row.getTimestamp("finished_at").toInstant().toString());
        return item;
    }

    private static Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", data);
        return response;
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        if (value == null || value.isBlank()) return java.util.UUID.randomUUID().toString();
        try {
            java.util.UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id must be a UUID", invalid);
        }
    }

    private record SubmissionResult(String internalSubmissionId, long publicSubmissionId, long homeworkId, String studentId, String evaluationStatus,
                                    BigDecimal autoScore, BigDecimal finalScore, String courseId) { }

    public record ReevaluateHomeworkRequest(@NotBlank @Size(max = 500) String reason) { }
}
