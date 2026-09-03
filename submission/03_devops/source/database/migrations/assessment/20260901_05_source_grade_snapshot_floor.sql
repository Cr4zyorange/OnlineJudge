-- A token issued before revision history existed cannot be reconstructed from
-- the current row.  Persist the earliest safe token per source so Grade gets a
-- conflict and restarts instead of mistaking an empty page for a valid snapshot.
ALTER TABLE assessment_source_grade_snapshot
  ADD COLUMN first_reconstructable_version BIGINT NOT NULL DEFAULT 1 AFTER snapshot_version;

UPDATE assessment_source_grade_snapshot snapshot
LEFT JOIN (
  SELECT source_type, source_id, MIN(snapshot_version) AS first_reconstructable_version
    FROM assessment_source_grade_revision
   GROUP BY source_type, source_id
) revision
  ON revision.source_type = snapshot.source_type
 AND revision.source_id = snapshot.source_id
SET snapshot.first_reconstructable_version =
  COALESCE(revision.first_reconstructable_version, snapshot.snapshot_version);
