CREATE INDEX IF NOT EXISTS idx_hwk_submission_effective
    ON t_hwk_submission (homework_id, is_final, is_deleted, submit_status, student_id);

CREATE INDEX IF NOT EXISTS idx_hwk_submission_attention
    ON t_hwk_submission (
        homework_id, is_final, is_deleted, submitted_at, id,
        submit_status, student_id, submit_type, evaluation_status, review_status
    );
