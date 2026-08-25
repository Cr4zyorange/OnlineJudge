ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_grade_analysis_snapshot_source
    ON t_grade_analysis_snapshot (
        course_id, target_type, grade_item_id, source_fingerprint, generated_at
    );
