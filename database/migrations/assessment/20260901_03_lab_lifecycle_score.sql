-- Issue #314: extend the durable LAB aggregate for lifecycle/query/score APIs.
ALTER TABLE assessment_lab_experiment
  ADD COLUMN IF NOT EXISTS chapter_id BIGINT NULL,
  ADD COLUMN IF NOT EXISTS attachment_ids VARCHAR(1000) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'DOCKER_IO',
  ADD COLUMN IF NOT EXISTS report_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS time_limit_ms INT NOT NULL DEFAULT 30000,
  ADD COLUMN IF NOT EXISTS memory_limit_kb INT NOT NULL DEFAULT 262144,
  ADD COLUMN IF NOT EXISTS published_at TIMESTAMP NULL,
  ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE assessment_lab_submission
  ADD COLUMN IF NOT EXISTS has_file BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE assessment_submission
  ADD COLUMN IF NOT EXISTS code_content TEXT NULL;

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
