-- #312 upgrade path for #341's canonical copy of DB-CRS-01..05.  MySQL 8.4
-- does not support ALTER TABLE ... ADD COLUMN IF NOT EXISTS, therefore every
-- additive operation is guarded through information_schema before executing.
-- This lets a resumed migration preserve existing Course facts instead of
-- failing after a partial cutover.
SET @oj312_schema = DATABASE();

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_course' AND column_name = 'roster_version') = 0,
    'ALTER TABLE crs_course ADD COLUMN roster_version BIGINT NOT NULL DEFAULT 0 AFTER is_deleted',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_course_member' AND column_name = 'member_version') = 0,
    'ALTER TABLE crs_course_member ADD COLUMN member_version BIGINT NOT NULL DEFAULT 1 AFTER is_deleted',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_resource' AND column_name = 'external_url') = 0,
    'ALTER TABLE crs_resource ADD COLUMN external_url VARCHAR(1024) NULL AFTER storage_key',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_resource' AND column_name = 'version') = 0,
    'ALTER TABLE crs_resource ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER file_size',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_resource' AND column_name = 'download_count') = 0,
    'ALTER TABLE crs_resource ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0 AFTER version',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;
