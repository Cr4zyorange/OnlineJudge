-- A mandatory publish can be broker-confirmed but returned as unroutable. Keep that fact durable
-- so an operator can add the missing binding and replay the same event id safely.
-- MySQL 8.4 does not support ALTER TABLE ... ADD COLUMN IF NOT EXISTS.  The
-- migration stream must also accept a pre-existing #313 table where these
-- columns were included by the first draft of 20260831_01.
SET @assessment_schema = DATABASE();
SET @delivery_attempt_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_event_outbox'
       AND column_name = 'delivery_attempt'
);
SET @delivery_attempt_sql = IF(
    @delivery_attempt_exists = 0,
    'ALTER TABLE assessment_event_outbox ADD COLUMN delivery_attempt INT NOT NULL DEFAULT 0',
    'DO 0'
);
PREPARE assessment_delivery_attempt_statement FROM @delivery_attempt_sql;
EXECUTE assessment_delivery_attempt_statement;
DEALLOCATE PREPARE assessment_delivery_attempt_statement;

SET @last_error_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_event_outbox'
       AND column_name = 'last_error'
);
SET @last_error_sql = IF(
    @last_error_exists = 0,
    'ALTER TABLE assessment_event_outbox ADD COLUMN last_error VARCHAR(1024) NULL',
    'DO 0'
);
PREPARE assessment_last_error_statement FROM @last_error_sql;
EXECUTE assessment_last_error_statement;
DEALLOCATE PREPARE assessment_last_error_statement;
