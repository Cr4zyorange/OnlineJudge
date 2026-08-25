ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(96) NULL;

ALTER TABLE t_grade_analysis_snapshot
    ALTER COLUMN source_fingerprint VARCHAR(96);

ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS total_student_count INT NULL;

ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS completed_count INT NULL;

ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS missing_count INT NULL;

ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS unsubmitted_count INT NULL;

ALTER TABLE t_grade_analysis_snapshot
    ADD COLUMN IF NOT EXISTS ungraded_count INT NULL;

CREATE INDEX IF NOT EXISTS idx_grade_analysis_snapshot_source
    ON t_grade_analysis_snapshot (
        course_id, target_type, grade_item_id, source_fingerprint, generated_at
    );

CREATE TABLE IF NOT EXISTS t_grade_analysis_source_version (
    course_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    grade_item_key BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    source_data_time DATETIME NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (course_id, target_type, grade_item_key)
);

CREATE INDEX IF NOT EXISTS idx_grade_analysis_source_version_updated
    ON t_grade_analysis_source_version (updated_at);
