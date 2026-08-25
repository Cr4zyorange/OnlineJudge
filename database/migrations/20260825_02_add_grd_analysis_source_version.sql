-- MySQL 8.x retained-volume upgrade for the Issue #253 snapshot fast path.
-- Count columns make a cache hit self-contained. The source-version table is
-- bumped transactionally by GRD repositories and avoids loading all grade rows.

DROP PROCEDURE IF EXISTS migrate_20260825_02_grd_analysis_source_version;

DELIMITER $$

CREATE PROCEDURE migrate_20260825_02_grd_analysis_source_version()
BEGIN
    DECLARE column_count INT DEFAULT 0;

    SELECT COUNT(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'total_student_count';
    IF column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot ADD COLUMN total_student_count INT NULL AFTER completion_rate;
    END IF;

    SELECT COUNT(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'completed_count';
    IF column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot ADD COLUMN completed_count INT NULL AFTER total_student_count;
    END IF;

    SELECT COUNT(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'missing_count';
    IF column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot ADD COLUMN missing_count INT NULL AFTER completed_count;
    END IF;

    SELECT COUNT(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'unsubmitted_count';
    IF column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot ADD COLUMN unsubmitted_count INT NULL AFTER missing_count;
    END IF;

    SELECT COUNT(*) INTO column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'ungraded_count';
    IF column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot ADD COLUMN ungraded_count INT NULL AFTER unsubmitted_count;
    END IF;

    CREATE TABLE IF NOT EXISTS t_grade_analysis_source_version (
        course_id BIGINT NOT NULL,
        target_type VARCHAR(30) NOT NULL,
        grade_item_key BIGINT NOT NULL,
        source_version BIGINT NOT NULL,
        source_data_time DATETIME NULL,
        updated_at DATETIME NOT NULL,
        PRIMARY KEY (course_id, target_type, grade_item_key),
        INDEX idx_grade_analysis_source_version_updated (updated_at)
    );
END$$

DELIMITER ;

CALL migrate_20260825_02_grd_analysis_source_version();
DROP PROCEDURE migrate_20260825_02_grd_analysis_source_version;
