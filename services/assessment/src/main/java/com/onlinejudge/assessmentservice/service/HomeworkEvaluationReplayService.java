package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.persistence.AssessmentOutboxRepository;
import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the durable worker task and the HWK-facing submission state consistent during replay. */
@Service
public class HomeworkEvaluationReplayService {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;
    private final SourceGradeRepository grades;
    private final AssessmentOutboxRepository outbox;

    public HomeworkEvaluationReplayService(JdbcTemplate jdbc, EvaluationTaskRepository tasks,
            SourceGradeRepository grades, AssessmentOutboxRepository outbox) {
        this.jdbc = jdbc;
        this.tasks = tasks;
        this.grades = grades;
        this.outbox = outbox;
    }

    @Transactional
    public boolean replay(EvaluationTask task, String requestedBy, String requestId, Instant now) {
        boolean currentHomeworkSubmission = jdbc.queryForObject("""
                SELECT is_final FROM assessment_homework_submission
                 WHERE submission_id = ? FOR UPDATE
                """, Boolean.class, task.submissionId());
        if (!tasks.manualReplayHomework(task.id(), requestedBy, now)) {
            return false;
        }
        int submissions = jdbc.update(
                "UPDATE assessment_submission SET evaluation_status = 'PENDING' WHERE id = ?",
                task.submissionId());
        int homeworkSubmissions = jdbc.update("""
                UPDATE assessment_homework_submission
                   SET evaluation_status = 'PENDING', auto_score = NULL, final_score = NULL
                 WHERE submission_id = ?
                """, task.submissionId());
        if (submissions != 1 || homeworkSubmissions != 1) {
            throw new IllegalStateException("homework replay must update both submission projections");
        }
        if (currentHomeworkSubmission) {
            grades.markUngradedIfPresent(task.sourceType(), task.sourceId(), task.studentId(), now)
                    .ifPresent(grade -> appendUngradedEvent(grade, requestId, now));
        }
        return true;
    }

    private void appendUngradedEvent(SourceGradeRepository.SourceGrade grade, String requestId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", grade.courseId());
        payload.put("sourceType", grade.sourceType());
        payload.put("sourceId", grade.sourceId());
        payload.put("studentId", grade.studentId());
        payload.put("score", null);
        payload.put("fullScore", grade.fullScore());
        payload.put("status", "UNGRADED");
        payload.put("sourceVersion", grade.sourceVersion());
        outbox.append("assessment.source-grade.changed.v2", "assessment-source-grade",
                grade.sourceType() + ":" + grade.sourceId() + ":" + grade.studentId(), grade.sourceVersion(),
                requestId, payload, now);
    }
}
