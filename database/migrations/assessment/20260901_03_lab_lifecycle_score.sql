-- Issue #314: extend the durable LAB aggregate for lifecycle/query/score APIs.
-- MySQL 8.4 does not support ALTER TABLE ... ADD COLUMN IF NOT EXISTS. Keep
-- this migration repeat-safe for databases created by either earlier LAB draft.
SET @assessment_schema = DATABASE();

SET @lab_chapter_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'chapter_id'
);
SET @lab_chapter_sql = IF(
    @lab_chapter_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN chapter_id BIGINT NULL',
    'DO 0'
);
PREPARE lab_chapter_statement FROM @lab_chapter_sql;
EXECUTE lab_chapter_statement;
DEALLOCATE PREPARE lab_chapter_statement;

SET @lab_attachment_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'attachment_ids'
);
SET @lab_attachment_sql = IF(
    @lab_attachment_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN attachment_ids VARCHAR(1000) NOT NULL DEFAULT ''''',
    'DO 0'
);
PREPARE lab_attachment_statement FROM @lab_attachment_sql;
EXECUTE lab_attachment_statement;
DEALLOCATE PREPARE lab_attachment_statement;

SET @lab_mode_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'evaluation_mode'
);
SET @lab_mode_sql = IF(
    @lab_mode_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN evaluation_mode VARCHAR(20) NOT NULL DEFAULT ''DOCKER_IO''',
    'DO 0'
);
PREPARE lab_mode_statement FROM @lab_mode_sql;
EXECUTE lab_mode_statement;
DEALLOCATE PREPARE lab_mode_statement;

SET @lab_report_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'report_required'
);
SET @lab_report_sql = IF(
    @lab_report_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN report_required BOOLEAN NOT NULL DEFAULT FALSE',
    'DO 0'
);
PREPARE lab_report_statement FROM @lab_report_sql;
EXECUTE lab_report_statement;
DEALLOCATE PREPARE lab_report_statement;

SET @lab_time_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'time_limit_ms'
);
SET @lab_time_sql = IF(
    @lab_time_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN time_limit_ms INT NOT NULL DEFAULT 30000',
    'DO 0'
);
PREPARE lab_time_statement FROM @lab_time_sql;
EXECUTE lab_time_statement;
DEALLOCATE PREPARE lab_time_statement;

SET @lab_memory_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'memory_limit_kb'
);
SET @lab_memory_sql = IF(
    @lab_memory_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN memory_limit_kb INT NOT NULL DEFAULT 262144',
    'DO 0'
);
PREPARE lab_memory_statement FROM @lab_memory_sql;
EXECUTE lab_memory_statement;
DEALLOCATE PREPARE lab_memory_statement;

SET @lab_published_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'published_at'
);
SET @lab_published_sql = IF(
    @lab_published_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN published_at TIMESTAMP NULL',
    'DO 0'
);
PREPARE lab_published_statement FROM @lab_published_sql;
EXECUTE lab_published_statement;
DEALLOCATE PREPARE lab_published_statement;

SET @lab_deleted_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_experiment'
       AND column_name = 'deleted'
);
SET @lab_deleted_sql = IF(
    @lab_deleted_exists = 0,
    'ALTER TABLE assessment_lab_experiment ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE',
    'DO 0'
);
PREPARE lab_deleted_statement FROM @lab_deleted_sql;
EXECUTE lab_deleted_statement;
DEALLOCATE PREPARE lab_deleted_statement;

SET @submission_file_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_lab_submission'
       AND column_name = 'has_file'
);
SET @submission_file_sql = IF(
    @submission_file_exists = 0,
    'ALTER TABLE assessment_lab_submission ADD COLUMN has_file BOOLEAN NOT NULL DEFAULT TRUE',
    'DO 0'
);
PREPARE submission_file_statement FROM @submission_file_sql;
EXECUTE submission_file_statement;
DEALLOCATE PREPARE submission_file_statement;

SET @submission_code_exists = (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = @assessment_schema
       AND table_name = 'assessment_submission'
       AND column_name = 'code_content'
);
SET @submission_code_sql = IF(
    @submission_code_exists = 0,
    'ALTER TABLE assessment_submission ADD COLUMN code_content TEXT NULL',
    'DO 0'
);
PREPARE submission_code_statement FROM @submission_code_sql;
EXECUTE submission_code_statement;
DEALLOCATE PREPARE submission_code_statement;

CREATE TABLE IF NOT EXISTS assessment_lab_score (
  submission_id VARCHAR(36) PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  report_id BIGINT NULL,
  auto_score DECIMAL(10,2) NULL,
  report_score DECIMAL(10,2) NULL,
  manual_score DECIMAL(10,2) NULL,
  final_score DECIMAL(10,2) NOT NULL,
  comment VARCHAR(2000) NULL,
  scored_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  INDEX idx_assessment_lab_score_lab (lab_id)
);
