-- Issue #314: carry the command origin through durable task execution and
-- align persisted score-comment bounds with API-LAB-13.
-- MySQL 8.4 needs information_schema guards for an upgraded #313 task table.
SET @assessment_schema = DATABASE();

SET @origin_request_exists = (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'evaluation_task'
       AND column_name = 'origin_request_id'
);
SET @origin_request_sql = IF(
    @origin_request_exists = 0,
    'ALTER TABLE evaluation_task ADD COLUMN origin_request_id VARCHAR(80) NOT NULL DEFAULT ''''',
    'DO 0'
);
PREPARE origin_request_statement FROM @origin_request_sql;
EXECUTE origin_request_statement;
DEALLOCATE PREPARE origin_request_statement;

-- Existing pre-correlation tasks had no external request identifier. Their task
-- UUID remains an inspectable legacy fallback; new commands always persist the
-- incoming X-Request-Id before a worker can claim the task.
UPDATE evaluation_task SET origin_request_id = id
 WHERE origin_request_id IS NULL OR origin_request_id = '';

ALTER TABLE assessment_lab_score MODIFY COLUMN comment VARCHAR(500) NULL;
