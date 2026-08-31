-- #315: retain immutable source-grade revisions so Grade can finish every page
-- against the snapshot token returned with page zero, even if a student is regraded.
ALTER TABLE assessment_source_grade
  ADD COLUMN snapshot_version BIGINT NOT NULL DEFAULT 1 AFTER source_version;

CREATE TABLE assessment_source_grade_revision (
  source_type VARCHAR(8) NOT NULL,
  source_id VARCHAR(80) NOT NULL,
  course_id VARCHAR(80) NOT NULL,
  student_id VARCHAR(80) NOT NULL,
  snapshot_version BIGINT NOT NULL,
  score DECIMAL(10,2),
  full_score DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  source_version BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (source_type, source_id, student_id, snapshot_version),
  INDEX idx_assessment_source_grade_revision_snapshot
    (course_id, source_type, source_id, snapshot_version, student_id)
);

-- The earlier source-grade projection held only the current value.  The first
-- revision is anchored at the existing source-wide watermark.  A pre-upgrade
-- token cannot be reconstructed from a current-only projection, so it must not
-- be allowed to read a newer value under an older version number.
UPDATE assessment_source_grade grade
JOIN assessment_source_grade_snapshot snapshot
  ON snapshot.source_type = grade.source_type
 AND snapshot.source_id = grade.source_id
SET grade.snapshot_version = snapshot.snapshot_version;

-- Later writes append a new row.
INSERT INTO assessment_source_grade_revision
  (source_type, source_id, course_id, student_id, snapshot_version, score,
   full_score, status, source_version, updated_at)
SELECT grade.source_type, grade.source_id, grade.course_id, grade.student_id,
       grade.snapshot_version, grade.score, grade.full_score, grade.status,
       grade.source_version, grade.updated_at
  FROM assessment_source_grade grade;
