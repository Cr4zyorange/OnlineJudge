-- #312 review: deleting a resource must not leave its authoritative object
-- behind on the persistent Course storage volume.  The journal is written in
-- the same transaction as the logical resource delete; the file itself is
-- removed after commit, and any crash between commit and cleanup leaves a
-- PENDING row that the recovery sweep retries until COMPLETED.
SET @oj312_schema = DATABASE();

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = @oj312_schema AND table_name = 'course_file_delete_journal') = 0,
    'CREATE TABLE course_file_delete_journal (
        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        course_id BIGINT NOT NULL,
        resource_id BIGINT NOT NULL,
        storage_key VARCHAR(500) NOT NULL,
        status VARCHAR(32) NOT NULL DEFAULT ''PENDING'',
        attempt_count INT NOT NULL DEFAULT 0,
        last_error VARCHAR(1024) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uq_course_file_delete_journal_resource (resource_id),
        KEY idx_course_file_delete_journal_pending (status, attempt_count)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;
