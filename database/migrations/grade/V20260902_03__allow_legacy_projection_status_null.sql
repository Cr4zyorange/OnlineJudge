-- V20260901_01 retained the legacy status column as NOT NULL.  The versioned
-- source-grade projection writes source_status instead, so a fresh MySQL
-- deployment otherwise rejects every valid projection fact before it can
-- advance the watermark.
ALTER TABLE grade_source_projection
    MODIFY status VARCHAR(32) NULL;
