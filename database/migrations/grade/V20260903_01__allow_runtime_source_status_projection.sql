-- V20260901_01 retained the legacy status column as NOT NULL. Runtime source
-- projection owns source_status after V20260901_02 and intentionally does not
-- write the retired column, so preserve existing values while allowing new
-- immutable source-grade facts to be projected.
ALTER TABLE grade_source_projection
    MODIFY status VARCHAR(32) NULL;
