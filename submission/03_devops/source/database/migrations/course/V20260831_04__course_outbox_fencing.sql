-- #312: a Course relay must never acknowledge an event after a newer relay
-- has recovered its lease.  The monotonically increasing generation fences
-- every publish/retry completion against the exact claim that sent it.
SET @oj312_schema = DATABASE();

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'course_event_outbox' AND column_name = 'lease_generation') = 0,
    'ALTER TABLE course_event_outbox ADD COLUMN lease_generation BIGINT NOT NULL DEFAULT 0 AFTER lease_until',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;
