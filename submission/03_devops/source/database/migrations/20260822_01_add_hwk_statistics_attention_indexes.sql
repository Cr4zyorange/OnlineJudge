-- MySQL 8.x retained-volume upgrade for Issue #225.
-- Run with the mysql client because DELIMITER is a client directive.
-- The clean upgrade adds both indexes in one atomic ALTER TABLE. The single-index
-- branches make a retry safe if an older/manual attempt left only one index.

DROP PROCEDURE IF EXISTS migrate_20260822_01_hwk_statistics_attention;

DELIMITER $$

CREATE PROCEDURE migrate_20260822_01_hwk_statistics_attention()
BEGIN
    DECLARE effective_index_count INT DEFAULT 0;
    DECLARE attention_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO effective_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_hwk_submission'
       AND index_name = 'idx_hwk_submission_effective';

    SELECT COUNT(*)
      INTO attention_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_hwk_submission'
       AND index_name = 'idx_hwk_submission_attention';

    IF effective_index_count = 0 AND attention_index_count = 0 THEN
        ALTER TABLE t_hwk_submission
            ADD INDEX idx_hwk_submission_effective (
                homework_id, is_final, is_deleted, submit_status, student_id
            ),
            ADD INDEX idx_hwk_submission_attention (
                homework_id, is_final, is_deleted, submitted_at, id,
                submit_status, student_id, submit_type, evaluation_status, review_status
            );
    ELSEIF effective_index_count = 0 THEN
        ALTER TABLE t_hwk_submission
            ADD INDEX idx_hwk_submission_effective (
                homework_id, is_final, is_deleted, submit_status, student_id
            );
    ELSEIF attention_index_count = 0 THEN
        ALTER TABLE t_hwk_submission
            ADD INDEX idx_hwk_submission_attention (
                homework_id, is_final, is_deleted, submitted_at, id,
                submit_status, student_id, submit_type, evaluation_status, review_status
            );
    END IF;
END$$

DELIMITER ;

CALL migrate_20260822_01_hwk_statistics_attention();
DROP PROCEDURE migrate_20260822_01_hwk_statistics_attention;
