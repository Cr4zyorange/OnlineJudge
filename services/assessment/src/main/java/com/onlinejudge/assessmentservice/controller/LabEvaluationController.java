package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.CourseMemberProjectionRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import com.onlinejudge.assessmentservice.security.CurrentUser;
import com.onlinejudge.assessmentservice.service.LabExperimentService;
import com.onlinejudge.assessmentservice.service.LabReportService;
import com.onlinejudge.assessmentservice.service.CoursePermissionClient;
import com.onlinejudge.assessmentservice.service.CourseMembershipGuard;
import com.onlinejudge.assessmentservice.storage.PersistentSubmissionFileStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.time.Instant;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Read-only LAB result projection.  It deliberately never invokes a worker or evaluator. */
@RestController
@RequestMapping("/api/v1/labs")
public class LabEvaluationController {
    private final LabExperimentService labs;
    private final EvaluationTaskRepository tasks;
    private final CourseMemberProjectionRepository courseMembers;
    private final CourseMembershipGuard membershipGuard;
    private final CoursePermissionClient coursePermissions;
    private final JdbcTemplate jdbc;
    private final SourceGradeRepository grades;
    private final AssessmentOutboxRepository outbox;
    private final LabReportService reports;
    private final PersistentSubmissionFileStore files;

    public LabEvaluationController(LabExperimentService labs, EvaluationTaskRepository tasks,
            CourseMemberProjectionRepository courseMembers, CoursePermissionClient coursePermissions, JdbcTemplate jdbc) {
        this(labs, tasks, courseMembers, coursePermissions, jdbc, null, null,
                new CourseMembershipGuard(courseMembers, coursePermissions), null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LabEvaluationController(LabExperimentService labs, EvaluationTaskRepository tasks,
            CourseMemberProjectionRepository courseMembers, CoursePermissionClient coursePermissions, JdbcTemplate jdbc,
            SourceGradeRepository grades, AssessmentOutboxRepository outbox, CourseMembershipGuard membershipGuard,
            LabReportService reports, PersistentSubmissionFileStore files) {
        this.labs = labs;
        this.tasks = tasks;
        this.courseMembers = courseMembers;
        this.membershipGuard = membershipGuard;
        this.coursePermissions = coursePermissions;
        this.jdbc = jdbc;
        this.grades = grades;
        this.outbox = outbox;
        this.reports = reports;
        this.files = files;
    }

    @GetMapping("/{labId}/submissions/{submissionId}/result")
    public Map<String, Object> result(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        return evaluationResult(labId, submissionId, user, false, requestIdOrGenerated(http));
    }

    /**
     * Builds the passive evaluation projection. Aggregated student results allow
     * manual LABs to have no task, while the dedicated result endpoint keeps its
     * historical not-found contract for an uncreated evaluation task.
     */
    private Map<String, Object> evaluationResult(long labId, String submissionId, CurrentUser user, boolean allowMissingTask, String requestId) {
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
        if (!manager && !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        EvaluationTask task = tasks.findBySubmission(submissionId).orElse(null);
        if (task == null && !allowMissingTask) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "evaluation task does not exist");
        }
        String persistedStatus = jdbc.query("SELECT evaluation_status FROM assessment_submission WHERE id = ?",
                rows -> rows.next() ? rows.getString(1) : "NONE", submissionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task == null ? null : task.id());
        response.put("submissionId", submissionId);
        response.put("evaluationStatus", task == null ? (persistedStatus == null ? "NONE" : persistedStatus)
                : task.resultStatus() == null ? "PENDING" : task.resultStatus());
        response.put("state", task == null ? "NONE" : task.state().name());
        boolean scoresVisible = manager || scoresPublished(lab);
        response.put("score", scoresVisible ? submission.autoScore() : null);
        response.put("fullScore", scoresVisible ? lab.maxScore() : null);
        response.put("evaluationVersion", task == null ? null : task.generation());
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
            item.put("score", scoresVisible ? rs.getBigDecimal("score") : null);
            item.put("input", rs.getBoolean("is_public") || manager ? rs.getString("input_text") : null);
            item.put("expectedOutput", rs.getBoolean("is_public") || manager ? rs.getString("expected_output") : null);
            item.put("actualOutput", rs.getString("actual_output"));
            item.put("message", rs.getString("message"));
            return item;
        }, submissionId);
        if (!manager) caseResults = caseResults.stream().filter(item -> jdbc.queryForObject("SELECT is_public FROM assessment_lab_testcase WHERE id = ?", Boolean.class, item.get("testcaseId"))).toList();
        response.put("passedCases", caseResults.stream().filter(item -> Boolean.TRUE.equals(item.get("passed"))).count());
        response.put("totalCases", caseResults.size());
        response.put("caseResults", caseResults);
        return response;
    }

    @GetMapping("/{labId}/submissions")
    public List<Map<String, Object>> submissions(@PathVariable long labId,
            @RequestParam(required = false) String studentId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        LabExperimentService.LabSummary lab = findLab(labId);
        boolean manager = canManage(lab, user);
        if (!manager && !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestIdOrGenerated(http))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        String requestedStudent = manager && studentId != null && !studentId.isBlank() ? studentId : (manager ? null : user.id());
        String sql = """
                SELECT submission_id, student_id, language, submit_status, submission_version, auto_score, final_score, has_file, submitted_at
                  FROM assessment_lab_submission
                 WHERE lab_id = ?
                """ + (requestedStudent == null ? "" : " AND student_id = ?") + " ORDER BY submitted_at DESC, submission_version DESC";
        return jdbc.query(sql, (rs, ignored) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("submissionId", rs.getString("submission_id"));
            item.put("labId", labId);
            item.put("studentId", rs.getString("student_id"));
            item.put("language", rs.getString("language"));
            item.put("submitStatus", rs.getString("submit_status"));
            String status = jdbc.query("SELECT evaluation_status FROM assessment_submission WHERE id = ?", rows -> rows.next() ? rows.getString(1) : "NONE", rs.getString("submission_id"));
            item.put("evaluationStatus", status == null ? "NONE" : status);
            boolean scoresVisible = manager || scoresPublished(lab);
            item.put("autoScore", scoresVisible ? rs.getBigDecimal("auto_score") : null);
            item.put("finalScore", scoresVisible ? rs.getBigDecimal("final_score") : null);
            item.put("version", rs.getInt("submission_version"));
            item.put("submittedAt", rs.getTimestamp("submitted_at").toInstant());
            int newer = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission WHERE lab_id = ? AND student_id = ? AND submission_version > ?", Integer.class, labId, rs.getString("student_id"), rs.getInt("submission_version"));
            item.put("isLatest", newer == 0);
            item.put("isFinal", rs.getBigDecimal("final_score") != null);
            item.put("isScoringBasis", rs.getBigDecimal("final_score") != null || newer == 0);
            item.put("hasFile", rs.getBoolean("has_file"));
            return item;
        }, requestedStudent == null ? new Object[]{labId} : new Object[]{labId, requestedStudent});
    }

    @GetMapping("/{labId}/submissions/{submissionId}")
    public Map<String, Object> submissionDetail(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        return submissionDetail(labId, submissionId, user, requestIdOrGenerated(http));
    }

    private Map<String, Object> submissionDetail(long labId, String submissionId, CurrentUser user, String requestId) {
        LabExperimentService.LabSummary lab = findLab(labId);
        Map<String, Object> item = jdbc.query("""
                SELECT submission_id, student_id, language, submit_status, submission_version, auto_score, final_score, has_file, submitted_at
                  FROM assessment_lab_submission WHERE submission_id = ? AND lab_id = ?
                """, (rs, ignored) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("submissionId", rs.getString("submission_id"));
            value.put("labId", labId);
            value.put("studentId", rs.getString("student_id"));
            value.put("language", rs.getString("language"));
            value.put("submitStatus", rs.getString("submit_status"));
            value.put("evaluationStatus", jdbc.query("SELECT evaluation_status FROM assessment_submission WHERE id = ?", rows -> rows.next() ? rows.getString(1) : "NONE", submissionId));
            value.put("autoScore", rs.getBigDecimal("auto_score"));
            value.put("finalScore", rs.getBigDecimal("final_score"));
            value.put("version", rs.getInt("submission_version"));
            value.put("submittedAt", rs.getTimestamp("submitted_at").toInstant());
            int newer = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_submission WHERE lab_id = ? AND student_id = ? AND submission_version > ?", Integer.class, labId, rs.getString("student_id"), rs.getInt("submission_version"));
            value.put("isLatest", newer == 0);
            value.put("isFinal", rs.getBigDecimal("final_score") != null);
            value.put("isScoringBasis", rs.getBigDecimal("final_score") != null || newer == 0);
            value.put("hasFile", rs.getBoolean("has_file"));
            value.put("code", jdbc.query("SELECT code_content FROM assessment_submission WHERE id = ?", rows -> rows.next() ? rows.getString(1) : null, submissionId));
            value.put("sourceFile", null);
            value.put("latestReport", null);
            return value;
        }, submissionId, labId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB submission does not exist"));
        boolean manager = canManage(lab, user);
        if (!manager && !item.get("studentId").equals(user.id())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LAB submission access is restricted");
        if (!manager && !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        boolean scoresVisible = manager || scoresPublished(lab);
        if (Boolean.TRUE.equals(item.get("hasFile"))) {
            sourceFile(labId, submissionId, lab.courseId(), item.get("studentId").toString()).ifPresent(source ->
                    item.put("sourceFile", source.publicView(manager)));
        }
        item.put("latestReport", latestReportForView(labId, submissionId, scoresVisible));
        item.put("latestScore", latestScoreForView(labId, submissionId, scoresVisible));
        if (!scoresVisible) {
            item.put("autoScore", null);
            item.put("finalScore", null);
            item.put("isFinal", false);
            item.put("isScoringBasis", false);
        }
        return item;
    }

    @GetMapping("/{labId}/submissions/{submissionId}/source/download")
    public ResponseEntity<byte[]> downloadSubmissionSource(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        if (!user.hasRole("TEACHER") && !user.hasRole("ADMIN")) {
            throw new LabAccessDeniedException("LAB source download is restricted to course managers");
        }
        LabExperimentService.LabSummary lab = findLab(labId);
        if (!canManage(lab, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        SourceFile source = sourceFile(labId, submissionId, lab.courseId(), null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "LAB source metadata is unavailable"));
        try {
            byte[] content = files.read(source.storageKey());
            if (content.length != source.fileSize()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LAB source file is inconsistent");
            }
            MediaType contentType;
            try { contentType = MediaType.parseMediaType(source.contentType()); }
            catch (IllegalArgumentException invalid) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(content.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(source.originalFilename(), StandardCharsets.UTF_8).build().toString())
                    .body(content);
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LAB source file is unavailable", unavailable);
        }
    }

    private java.util.Optional<SourceFile> sourceFile(long labId, String submissionId, String courseId, String uploaderId) {
        String sql = """
                SELECT submission_id, lab_id, course_id, uploader_id, storage_key, original_filename, content_type, file_size
                  FROM assessment_lab_submission_source_file
                 WHERE submission_id = ? AND lab_id = ? AND course_id = ? AND status = 'AVAILABLE'
                """ + (uploaderId == null ? "" : " AND uploader_id = ?");
        Object[] parameters = uploaderId == null
                ? new Object[]{submissionId, labId, courseId}
                : new Object[]{submissionId, labId, courseId, uploaderId};
        return jdbc.query(sql, (rs, ignored) -> new SourceFile(
                rs.getString("submission_id"), rs.getString("storage_key"), rs.getString("original_filename"),
                rs.getString("content_type"), rs.getLong("file_size")), parameters).stream().findFirst();
    }

    private record SourceFile(String submissionId, String storageKey, String originalFilename,
                              String contentType, long fileSize) {
        Map<String, Object> publicView(boolean downloadAvailable) {
            return Map.of("originalFilename", originalFilename, "contentType", contentType,
                    "fileSize", fileSize, "downloadAvailable", downloadAvailable);
        }
    }

    @GetMapping("/{labId}/results/{studentId}")
    public Map<String, Object> studentResult(@PathVariable long labId, @PathVariable String studentId,
            @RequestAttribute("assessment.currentUser") CurrentUser user, HttpServletRequest http) {
        LabExperimentService.LabSummary lab = findLab(labId);
        boolean manager = canManage(lab, user);
        if (!manager && !user.id().equals(studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LAB result access is restricted");
        }
        String requestId = requestIdOrGenerated(http);
        if (!manager && !membershipGuard.isActiveMember(lab.courseId(), user.id(), requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active course membership is required");
        }
        String submissionId = jdbc.query("""
                SELECT submission_id FROM assessment_lab_submission
                 WHERE lab_id = ? AND student_id = ?
                 ORDER BY CASE WHEN final_score IS NOT NULL THEN 0 ELSE 1 END,
                          submission_version DESC, submitted_at DESC
                 LIMIT 1
                """, rows -> rows.next() ? rows.getString(1) : null, labId, studentId);
        if (submissionId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB result does not exist");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("labId", labId);
        response.put("courseId", lab.courseId());
        response.put("studentId", studentId);
        response.put("status", lab.status());
        response.put("publishedAt", lab.publishedAt());
        boolean scoresVisible = manager || scoresPublished(lab);
        // The aggregate always exposes the submission and public testcase feedback;
        // only score-bearing fields are gated until the teacher releases them.
        response.put("submission", submissionDetail(labId, submissionId, user, requestId));
        response.put("evaluationResult", evaluationResult(labId, submissionId, user, true, requestId));
        response.put("latestScore", latestScoreForView(labId, submissionId, scoresVisible));
        response.put("latestReport", latestReportForView(labId, submissionId, scoresVisible));
        return response;
    }

    private Map<String, Object> latestScoreForView(long labId, String submissionId, boolean scoresVisible) {
        if (!scoresVisible) return null;
        return jdbc.query("""
                SELECT report_id, auto_score, report_score, manual_score, final_score, comment, scored_at, updated_at
                  FROM assessment_lab_score
                 WHERE lab_id = ? AND submission_id = ?
                """, (rs, ignored) -> {
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("reportId", rs.getObject("report_id"));
            score.put("autoScore", rs.getBigDecimal("auto_score"));
            score.put("reportScore", rs.getBigDecimal("report_score"));
            score.put("manualScore", rs.getBigDecimal("manual_score"));
            score.put("finalScore", rs.getBigDecimal("final_score"));
            score.put("comment", rs.getString("comment"));
            score.put("scoredAt", rs.getTimestamp("scored_at") == null ? null : rs.getTimestamp("scored_at").toInstant());
            score.put("updatedAt", rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant());
            return score;
        }, labId, submissionId).stream().findFirst().orElse(null);
    }

    private LabReportService.LabReportSummary latestReportForView(long labId, String submissionId, boolean scoresVisible) {
        if (reports == null) return null;
        LabReportService.LabReportSummary report = reports.latestForSubmission(labId, submissionId);
        if (report == null || scoresVisible) return report;
        return new LabReportService.LabReportSummary(report.reportId(), report.submissionId(), report.fileName(),
                report.fileType(), report.fileSize(), report.version(), null, null, report.submittedAt(), report.downloadUrl());
    }

    private LabExperimentService.LabSummary findLab(long labId) {
        try { return labs.find(labId); }
        catch (java.util.NoSuchElementException missing) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB does not exist", missing); }
    }

    private boolean canManage(LabExperimentService.LabSummary lab, CurrentUser user) {
        return (user.hasRole("TEACHER") || user.hasRole("ADMIN")) && coursePermissions.canManageCourse(lab.courseId(), user.id());
    }

    private boolean canManage(LabExperimentService.LabSummary lab, CurrentUser user, String requestId) {
        return (user.hasRole("TEACHER") || user.hasRole("ADMIN"))
                && coursePermissions.canManageCourse(lab.courseId(), user.id(), requestId);
    }

    private boolean scoresPublished(LabExperimentService.LabSummary lab) {
        return "SCORE_PUBLISHED".equals(lab.status()) || "ARCHIVED".equals(lab.status());
    }

    @org.springframework.web.bind.annotation.PostMapping("/{labId}/submissions/{submissionId}/score")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> score(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user,
            @org.springframework.web.bind.annotation.RequestBody ScoreRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        String requestId = requireRequestId(http);
        LabExperimentService.LabSummary lab = findLab(labId);
        if (!canManage(lab, user, requestId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        if ("ARCHIVED".equals(lab.status()) || lab.deleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "an archived LAB is immutable");
        }
        Map<String, Object> submission = jdbc.query("SELECT student_id, auto_score FROM assessment_lab_submission WHERE lab_id = ? AND submission_id = ? FOR UPDATE",
                (rs, ignored) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("studentId", rs.getString("student_id"));
                    value.put("autoScore", rs.getBigDecimal("auto_score"));
                    return value;
                }, labId, submissionId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB submission does not exist"));
        validateScoreRequest(lab, request);
        BigDecimal previousFinalScore = jdbc.query("SELECT final_score FROM assessment_lab_score WHERE submission_id = ? FOR UPDATE",
                rows -> rows.next() ? rows.getBigDecimal(1) : null, submissionId);
        String changeReason = request.changeReason() == null ? null : request.changeReason().trim();
        if (changeReason != null && changeReason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "changeReason must be at most 500 characters");
        }
        boolean scoreChanged = previousFinalScore != null && previousFinalScore.compareTo(request.finalScore()) != 0;
        if (scoreChanged && (changeReason == null || changeReason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "changeReason is required when changing an existing score");
        }
        Instant now = Instant.now();
        Long reportId = reports == null ? null : java.util.Optional.ofNullable(reports.latestForSubmission(labId, submissionId))
                .map(LabReportService.LabReportSummary::reportId).orElse(null);
        jdbc.update("""
                INSERT INTO assessment_lab_score (submission_id, lab_id, report_id, auto_score, report_score, manual_score, final_score, comment, scored_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE report_id = VALUES(report_id), report_score = VALUES(report_score), manual_score = VALUES(manual_score),
                    final_score = VALUES(final_score), comment = VALUES(comment), updated_at = VALUES(updated_at)
                """, submissionId, labId, reportId, submission.get("autoScore"), request.reportScore(), request.manualScore(), request.finalScore(), request.comment(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbc.update("UPDATE assessment_lab_submission SET final_score = ?, submit_status = 'SCORED' WHERE submission_id = ? AND lab_id = ?", request.finalScore(), submissionId, labId);
        if (scoreChanged) {
            jdbc.update("""
                    INSERT INTO assessment_lab_score_change_log
                        (submission_id, old_final_score, new_final_score, reason, operator_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, submissionId, previousFinalScore, request.finalScore(), changeReason, user.id(), java.sql.Timestamp.from(now));
        }
        if (grades != null && outbox != null && scoresPublished(lab)) {
            long version = grades.upsertScored("LAB", Long.toString(labId), lab.courseId(), (String) submission.get("studentId"), request.finalScore(), lab.maxScore(), now);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("courseId", lab.courseId());
            payload.put("sourceType", "LAB");
            payload.put("sourceId", Long.toString(labId));
            payload.put("studentId", submission.get("studentId"));
            payload.put("score", request.finalScore());
            payload.put("fullScore", lab.maxScore());
            payload.put("status", "SCORED");
            payload.put("sourceVersion", version);
            outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade", "LAB:" + labId + ":" + submission.get("studentId"), version, requestId, payload, now);
        }
        return jdbc.query("SELECT submission_id, report_id, auto_score, report_score, manual_score, final_score, comment, scored_at, updated_at FROM assessment_lab_score WHERE submission_id = ?",
                (rs, ignored) -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("submissionId", rs.getString("submission_id"));
                    response.put("reportId", rs.getObject("report_id"));
                    response.put("autoScore", rs.getBigDecimal("auto_score"));
                    response.put("reportScore", rs.getBigDecimal("report_score"));
                    response.put("manualScore", rs.getBigDecimal("manual_score"));
                    response.put("finalScore", rs.getBigDecimal("final_score"));
                    response.put("comment", rs.getString("comment"));
                    response.put("hasChangeLogs", jdbc.queryForObject("SELECT COUNT(*) FROM assessment_lab_score_change_log WHERE submission_id = ?", Integer.class, submissionId) > 0);
                    response.put("scoredAt", rs.getTimestamp("scored_at").toInstant());
                    response.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
                    return response;
                }, submissionId).stream().findFirst().orElseThrow();
    }

    @GetMapping("/{labId}/statistics")
    public Map<String, Object> statistics(@PathVariable long labId,
            @RequestAttribute("assessment.currentUser") CurrentUser user) {
        LabExperimentService.LabSummary lab = findLab(labId);
        if (!canManage(lab, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        String creator = jdbc.queryForObject("SELECT created_by FROM assessment_lab_experiment WHERE id = ?", String.class, labId);
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_course_member_projection WHERE course_id = ? AND membership_status = 'ACTIVE' AND user_id <> ?", Integer.class, lab.courseId(), creator);
        int submitted = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT submission.student_id) FROM assessment_lab_submission submission
                 JOIN assessment_course_member_projection member ON member.course_id = ? AND member.user_id = submission.student_id
                    AND member.membership_status = 'ACTIVE' AND member.user_id <> ?
                 WHERE submission.lab_id = ?
                """, Integer.class, lab.courseId(), creator, labId);
        int evaluated = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                %s
                ) effective
                 JOIN assessment_submission submission ON submission.id = effective.submission_id
                 WHERE submission.evaluation_status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','SYSTEM_ERROR')
                """.formatted(effectiveSubmissionsSql("s.submission_id", true)), Integer.class, lab.courseId(), creator, labId);
        List<BigDecimal> effectiveScores = jdbc.query(effectiveSubmissionsSql("COALESCE(s.final_score, s.auto_score) AS score", true),
                (rs, ignored) -> rs.getBigDecimal("score"), lab.courseId(), creator, labId);
        List<BigDecimal> scored = effectiveScores.stream().filter(java.util.Objects::nonNull).toList();
        BigDecimal average = scored.isEmpty() ? null : scored.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
        int late = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assessment_lab_submission submission
                 JOIN assessment_course_member_projection member ON member.course_id = ? AND member.user_id = submission.student_id
                    AND member.membership_status = 'ACTIVE' AND member.user_id <> ?
                 WHERE submission.lab_id = ? AND submission.submit_status = 'LATE'
                """, Integer.class, lab.courseId(), creator, labId);
        List<String> unsubmitted = jdbc.queryForList("""
                SELECT member.user_id FROM assessment_course_member_projection member
                 WHERE member.course_id = ? AND member.membership_status = 'ACTIVE' AND member.user_id <> ?
                   AND NOT EXISTS (SELECT 1 FROM assessment_lab_submission submission WHERE submission.lab_id = ? AND submission.student_id = member.user_id)
                 ORDER BY member.user_id
                """, String.class, lab.courseId(), creator, labId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("labId", labId);
        response.put("courseId", lab.courseId());
        response.put("totalStudentCount", total);
        response.put("submittedCount", submitted);
        response.put("unsubmittedCount", Math.max(0, total - submitted));
        response.put("evaluatedCount", evaluated);
        response.put("submissionRate", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(submitted * 100.0 / total));
        response.put("evaluationCompletionRate", submitted == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(evaluated * 100.0 / submitted));
        response.put("averageScore", average);
        response.put("lateSubmissionCount", late);
        response.put("unsubmittedStudentIds", unsubmitted);
        response.put("scoreDistribution", scoreDistribution(scored));
        response.put("generatedAt", Instant.now());
        return response;
    }

    @org.springframework.web.bind.annotation.PostMapping("/{labId}/submissions/{submissionId}/evaluate")
    public Map<String, Object> evaluate(@PathVariable long labId, @PathVariable String submissionId,
            @RequestAttribute("assessment.currentUser") CurrentUser user,
            jakarta.servlet.http.HttpServletRequest request) {
        if (request.getHeader("X-Request-Id") == null || request.getHeader("X-Request-Id").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required");
        }
        LabExperimentService.LabSummary lab = findLab(labId);
        String requestId = requireRequestId(request);
        if (!canManage(lab, user, requestId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "course management permission is required");
        }
        EvaluationTask task = tasks.findBySubmission(submissionId)
                .filter(candidate -> "LAB".equals(candidate.sourceType()) && Long.toString(labId).equals(candidate.sourceId()))
                .orElse(null);
        if (task == null) {
            Map<String, Object> submission = jdbc.query("SELECT student_id FROM assessment_lab_submission WHERE submission_id = ? AND lab_id = ?",
                    (rs, ignored) -> Map.<String, Object>of("studentId", rs.getString("student_id")), submissionId, labId)
                    .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LAB submission does not exist"));
            String taskId = java.util.UUID.randomUUID().toString();
            Instant now = Instant.now();
            tasks.insert(taskId, submissionId, "LAB", Long.toString(labId), lab.courseId(), (String) submission.get("studentId"), requestId, now);
            jdbc.update("UPDATE assessment_submission SET evaluation_status = 'PENDING' WHERE id = ?", submissionId);
            EvaluationTask created = tasks.find(taskId).orElseThrow();
            return Map.of("taskId", created.id(), "submissionId", submissionId, "state", created.state().name(), "generation", created.generation());
        }
        if (!tasks.manualReplay(task.id(), user.id(), requestId, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a terminal failed evaluation can be replayed");
        }
        EvaluationTask replayed = tasks.find(task.id()).orElseThrow();
        return Map.of("taskId", replayed.id(), "submissionId", submissionId, "state", replayed.state().name(), "generation", replayed.generation());
    }

    public record ScoreRequest(BigDecimal manualScore, BigDecimal reportScore, BigDecimal finalScore, String comment, String changeReason) { }

    private record LabSubmission(String studentId, BigDecimal autoScore) { }

    private void validateScoreRequest(LabExperimentService.LabSummary lab, ScoreRequest request) {
        if (request == null || request.manualScore() == null || request.finalScore() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manualScore and finalScore are required");
        }
        validateScoreComponent("manualScore", request.manualScore(), lab.maxScore());
        validateScoreComponent("finalScore", request.finalScore(), lab.maxScore());
        if (request.reportScore() != null) validateScoreComponent("reportScore", request.reportScore(), lab.maxScore());
        if (request.comment() != null && request.comment().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comment must be at most 500 characters");
        }
    }

    private static void validateScoreComponent(String name, BigDecimal value, BigDecimal maxScore) {
        if (value.signum() < 0 || value.compareTo(maxScore) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " must be within the LAB score range");
        }
    }

    private static String requireRequestId(jakarta.servlet.http.HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank() || requestId.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Request-Id is required and must be at most 80 characters");
        }
        return requestId;
    }

    private static String requestIdOrGenerated(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? java.util.UUID.randomUUID().toString() : requestId;
    }

    private static String effectiveSubmissionsSql(String projection, boolean activeRosterOnly) {
        return """
                SELECT %s
                  FROM assessment_lab_submission s
                """.formatted(projection) + (activeRosterOnly ? """
                  JOIN assessment_course_member_projection member ON member.course_id = ? AND member.user_id = s.student_id
                     AND member.membership_status = 'ACTIVE' AND member.user_id <> ?
                """ : "") + """
                 WHERE s.lab_id = ?
                   AND ((s.final_score IS NOT NULL AND s.submission_version = (SELECT MAX(finalized.submission_version)
                          FROM assessment_lab_submission finalized
                         WHERE finalized.lab_id = s.lab_id AND finalized.student_id = s.student_id
                           AND finalized.final_score IS NOT NULL))
                     OR (s.final_score IS NULL AND NOT EXISTS (SELECT 1 FROM assessment_lab_submission finalized
                          WHERE finalized.lab_id = s.lab_id AND finalized.student_id = s.student_id
                            AND finalized.final_score IS NOT NULL)
                         AND s.submission_version = (SELECT MAX(latest.submission_version)
                              FROM assessment_lab_submission latest
                             WHERE latest.lab_id = s.lab_id AND latest.student_id = s.student_id)))
                """;
    }

    private static Map<String, Integer> scoreDistribution(List<BigDecimal> scores) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0-59", 0);
        distribution.put("60-69", 0);
        distribution.put("70-79", 0);
        distribution.put("80-89", 0);
        distribution.put("90-100", 0);
        for (BigDecimal score : scores) {
            String bucket = score.compareTo(BigDecimal.valueOf(60)) < 0 ? "0-59"
                    : score.compareTo(BigDecimal.valueOf(70)) < 0 ? "60-69"
                    : score.compareTo(BigDecimal.valueOf(80)) < 0 ? "70-79"
                    : score.compareTo(BigDecimal.valueOf(90)) < 0 ? "80-89" : "90-100";
            distribution.compute(bucket, (ignored, count) -> count == null ? 1 : count + 1);
        }
        return distribution;
    }
}
