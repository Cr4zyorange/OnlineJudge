package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.LabExperimentService;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Read-only LAB result projection.  It deliberately never invokes a worker or evaluator. */
@RestController
@RequestMapping("/api/v1/labs")
public class LabEvaluationController {
    private final LabExperimentService labs;
    private final EvaluationTaskRepository tasks;
    private final CourseMemberProjectionRepository courseMembers;
    private final CoursePermissionClient coursePermissions;
    private final JdbcTemplate jdbc;

    public LabEvaluationController(LabExperimentService labs, EvaluationTaskRepository tasks,
            CourseMemberProjectionRepository courseMembers, CoursePermissionClient coursePermissions, JdbcTemplate jdbc) {
        this.labs = labs;
        this.tasks = tasks;
        this.courseMembers = courseMembers;
        this.coursePermissions = coursePermissions;
        this.jdbc = jdbc;
    }

    @GetMapping("/{labId}/submissions/{submissionId}/result")
    public Map<String, Object> result(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        LabExperimentService.LabSummary lab;
        try { lab = labs.find(labId); }
        catch (java.util.NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
        LabSubmission submission = jdbc.query("""
                SELECT student_id, auto_score FROM assessment_lab_submission
                 WHERE submission_id = ? AND lab_id = ?
                """, (rs, ignored) -> new LabSubmission(rs.getString("student_id"), rs.getBigDecimal("auto_score")), submissionId, labId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB submission does not exist"));
        boolean owner = user.id().equals(submission.studentId());
        boolean manager = (user.hasRole("TEACHER") || user.hasRole("ADMIN")) && coursePermissions.canManageCourse(lab.courseId(), user.id());
        if (!owner && !manager) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LAB result access is restricted");
        EvaluationTask task = tasks.findBySubmission(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation task does not exist"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.id());
        response.put("submissionId", submissionId);
        response.put("evaluationStatus", task.resultStatus() == null ? "PENDING" : task.resultStatus());
        response.put("state", task.state().name());
        response.put("score", submission.autoScore());
        response.put("fullScore", lab.maxScore());
        response.put("evaluationVersion", task.generation());
        List<Map<String, Object>> caseResults = jdbc.query("""
                SELECT result.testcase_id, result.passed, result.score, result.actual_output, result.message,
                       testcase.input_text, testcase.expected_output, testcase.order_num, testcase.is_public
                  FROM assessment_lab_evaluation_result result
                  JOIN assessment_lab_testcase testcase ON testcase.id = result.testcase_id
                 WHERE result.submission_id = ? ORDER BY testcase.order_num, testcase.id
                """, (rs, ignored) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("testcaseId", rs.getLong("testcase_id"));
            item.put("orderNum", rs.getInt("order_num"));
            item.put("passed", rs.getBoolean("passed"));
            item.put("score", rs.getBigDecimal("score"));
            item.put("input", rs.getString("input_text"));
            item.put("expectedOutput", rs.getString("expected_output"));
            item.put("actualOutput", rs.getString("actual_output"));
            item.put("message", rs.getString("message"));
            return item;
        }, submissionId);
        if (!manager) {
            caseResults = caseResults.stream().filter(item -> jdbc.queryForObject("SELECT is_public FROM assessment_lab_testcase WHERE id = ?", Boolean.class, item.get("testcaseId"))).toList();
        }
        response.put("passedCases", caseResults.stream().filter(item -> Boolean.TRUE.equals(item.get("passed"))).count());
        response.put("totalCases", caseResults.size());
        response.put("caseResults", caseResults);
        return response;
    }

    private record LabSubmission(String studentId, BigDecimal autoScore) { }
}
