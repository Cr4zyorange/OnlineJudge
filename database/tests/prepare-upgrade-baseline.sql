-- Test-only downgrade from the current clean snapshot to the retained-volume baseline
-- that predates the two 2026-08-25 GRD migrations and migration history table.
DROP TABLE IF EXISTS t_grade_analysis_source_version;
DROP INDEX idx_grade_analysis_snapshot_source ON t_grade_analysis_snapshot;
ALTER TABLE t_grade_analysis_snapshot
    DROP COLUMN source_fingerprint,
    DROP COLUMN total_student_count,
    DROP COLUMN completed_count,
    DROP COLUMN missing_count,
    DROP COLUMN unsubmitted_count,
    DROP COLUMN ungraded_count;
DROP TABLE schema_migrations;
