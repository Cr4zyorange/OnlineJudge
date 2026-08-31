package com.onlinejudge.assessmentservice.service;

import com.onlinejudge.assessmentservice.persistence.EvaluationTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Keeps the durable worker task and the HWK-facing submission state consistent during replay. */
@Service
public class HomeworkEvaluationReplayService {
    private final JdbcTemplate jdbc;
    private final EvaluationTaskRepository tasks;

    public HomeworkEvaluationReplayService(JdbcTemplate jdbc, EvaluationTaskRepository tasks) {
        this.jdbc = jdbc;
        this.tasks = tasks;
    }

    @Transactional
    public boolean replay(String taskId, String submissionId, String requestedBy, Instant now) {
        if (!tasks.manualReplayHomework(taskId, requestedBy, now)) {
            return false;
        }
        int submissions = jdbc.update(
                "UPDATE assessment_submission SET evaluation_status = 'PENDING' WHERE id = ?",
                submissionId);
        int homeworkSubmissions = jdbc.update("""
                UPDATE assessment_homework_submission
                   SET evaluation_status = 'PENDING', auto_score = NULL, final_score = NULL
                 WHERE submission_id = ?
                """, submissionId);
        if (submissions != 1 || homeworkSubmissions != 1) {
            throw new IllegalStateException("homework replay must update both submission projections");
        }
        return true;
    }
}
