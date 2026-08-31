package com.onlinejudge.assessmentservice.persistence;

import com.onlinejudge.assessmentservice.model.EvaluationTask;
import com.onlinejudge.assessmentservice.model.TaskState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class EvaluationTaskRepository {
    private final JdbcTemplate jdbc;

    public EvaluationTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String id, String submissionId, String sourceType, String sourceId,
                       String courseId, String studentId, Instant now) {
        jdbc.update("""
                INSERT INTO evaluation_task (id, submission_id, source_type, source_id, course_id, student_id,
                    state, generation, attempt, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, 0, ?, ?, ?)
                """, id, submissionId, sourceType, sourceId, courseId, studentId,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    /**
     * The candidate read may race, but the conditional update is the claim.  A losing worker
     * obtains zero rows and retries, so no task is ever owned by two generations.
     */
    @Transactional
    public Optional<EvaluationTask> claimNext(String workerId, Instant now, Duration lease) {
        List<String> candidates = jdbc.queryForList("""
                SELECT id FROM evaluation_task
                 WHERE state = 'PENDING'
                    OR (state = 'RETRY_WAIT' AND next_attempt_at <= ?)
                    OR (state = 'RUNNING' AND lease_until < ?)
                 ORDER BY created_at ASC
                 LIMIT 1
                """, String.class, Timestamp.from(now), Timestamp.from(now));
        if (candidates.isEmpty()) return Optional.empty();
        String id = candidates.getFirst();
        int updated = jdbc.update("""
                UPDATE evaluation_task
                   SET state = 'RUNNING', lease_owner = ?, lease_until = ?, heartbeat_at = ?, next_attempt_at = NULL,
                       generation = generation + 1, attempt = attempt + 1, updated_at = ?
                 WHERE id = ?
                   AND (state = 'PENDING' OR (state = 'RETRY_WAIT' AND next_attempt_at <= ?) OR (state = 'RUNNING' AND lease_until < ?))
                """, workerId, Timestamp.from(now.plus(lease)), Timestamp.from(now), Timestamp.from(now),
                id, Timestamp.from(now), Timestamp.from(now));
        return updated == 1 ? find(id) : Optional.empty();
    }

    public boolean heartbeat(String id, String workerId, long generation, Instant now, Duration lease) {
        return jdbc.update("""
                UPDATE evaluation_task SET heartbeat_at = ?, lease_until = ?, updated_at = ?
                 WHERE id = ? AND state = 'RUNNING' AND lease_owner = ? AND generation = ? AND lease_until >= ?
                """, Timestamp.from(now), Timestamp.from(now.plus(lease)), Timestamp.from(now), id, workerId,
                generation, Timestamp.from(now)) == 1;
    }

    /** The generation and unexpired lease are the fencing token for terminal writes. */
    public boolean complete(String id, String workerId, long generation, boolean successful, String resultStatus, Instant now) {
        return jdbc.update("""
                UPDATE evaluation_task SET state = ?, result_status = ?, finished_at = ?, updated_at = ?,
                    lease_owner = NULL, lease_until = NULL
                 WHERE id = ? AND state = 'RUNNING' AND lease_owner = ? AND generation = ? AND lease_until >= ?
                """, successful ? "SUCCEEDED" : "FAILED", resultStatus, Timestamp.from(now), Timestamp.from(now),
                id, workerId, generation, Timestamp.from(now)) == 1;
    }

    /** A retry is also fenced: only a holder of the live lease may make work claimable again. */
    public boolean reschedule(String id, String workerId, long generation, String resultStatus, Instant nextAttemptAt, Instant now) {
        return jdbc.update("""
                UPDATE evaluation_task SET state = 'RETRY_WAIT', result_status = ?, next_attempt_at = ?, updated_at = ?,
                    lease_owner = NULL, lease_until = NULL
                 WHERE id = ? AND state = 'RUNNING' AND lease_owner = ? AND generation = ? AND lease_until >= ?
                """, resultStatus, Timestamp.from(nextAttemptAt), Timestamp.from(now), id, workerId, generation, Timestamp.from(now)) == 1;
    }

    /** Explicit teacher replay advances the fencing generation before returning a terminal failure to PENDING. */
    public boolean manualReplay(String id, String requestedBy, Instant now) {
        return jdbc.update("""
                UPDATE evaluation_task SET state = 'PENDING', result_status = NULL, next_attempt_at = ?, lease_owner = NULL,
                    lease_until = NULL, generation = generation + 1, manual_replay_count = manual_replay_count + 1,
                    manual_replayed_by = ?, manual_replayed_at = ?, updated_at = ?
                 WHERE id = ? AND state = 'FAILED'
                """, Timestamp.from(now), requestedBy, Timestamp.from(now), Timestamp.from(now), id) == 1;
    }

    public Optional<EvaluationTask> find(String id) {
        List<EvaluationTask> rows = jdbc.query("""
                SELECT id, submission_id, source_type, source_id, course_id, student_id, state, generation,
                       lease_owner, lease_until, attempt, result_status
                  FROM evaluation_task WHERE id = ?
                """, (rs, ignored) -> new EvaluationTask(rs.getString("id"), rs.getString("submission_id"),
                rs.getString("source_type"), rs.getString("source_id"), rs.getString("course_id"),
                rs.getString("student_id"), TaskState.valueOf(rs.getString("state")), rs.getLong("generation"),
                rs.getString("lease_owner"), toInstant(rs.getTimestamp("lease_until")), rs.getInt("attempt"),
                rs.getString("result_status")), id);
        return rows.stream().findFirst();
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task", Integer.class); }
    private static Instant toInstant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
