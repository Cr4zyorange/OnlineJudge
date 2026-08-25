-- MySQL 8.x retained-volume upgrade for Issue #253.
-- Existing snapshots remain readable with a NULL fingerprint; the service writes
-- SHA-256 fingerprints for new snapshots and replaces a legacy latest snapshot once.

DROP PROCEDURE IF EXISTS migrate_20260825_01_grd_analysis_source_fingerprint;

DELIMITER $$

CREATE PROCEDURE migrate_20260825_01_grd_analysis_source_fingerprint()
BEGIN
    DECLARE fingerprint_column_count INT DEFAULT 0;
    DECLARE fingerprint_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO fingerprint_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND column_name = 'source_fingerprint';

    IF fingerprint_column_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot
            ADD COLUMN source_fingerprint VARCHAR(64) NULL AFTER source_data_time;
    END IF;

    SELECT COUNT(*)
      INTO fingerprint_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_grade_analysis_snapshot'
       AND index_name = 'idx_grade_analysis_snapshot_source';

    IF fingerprint_index_count = 0 THEN
        ALTER TABLE t_grade_analysis_snapshot
            ADD INDEX idx_grade_analysis_snapshot_source (
                course_id, target_type, grade_item_id, source_fingerprint, generated_at
            );
    END IF;
END$$

DELIMITER ;

CALL migrate_20260825_01_grd_analysis_source_fingerprint();
DROP PROCEDURE migrate_20260825_01_grd_analysis_source_fingerprint;
