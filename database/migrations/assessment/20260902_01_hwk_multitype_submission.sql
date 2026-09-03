-- Issue #320: preserve the full HWK TEXT submission/review contract in the
-- Assessment-owned schema.  Existing CODE rows keep their existing values.
ALTER TABLE assessment_homework_submission
  ADD COLUMN submit_type VARCHAR(20) NOT NULL DEFAULT 'CODE' AFTER submission_version,
  ADD COLUMN answer_text TEXT NULL AFTER language,
  ADD COLUMN answer_json TEXT NULL AFTER answer_text,
  ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'NEED_REVIEW' AFTER evaluation_status,
  ADD COLUMN manual_score DECIMAL(10,2) NULL AFTER auto_score,
  ADD COLUMN review_comment TEXT NULL AFTER final_score;
