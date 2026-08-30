-- #337 P0: a member row proves only one aggregate.  This course-scoped
-- watermark is set only by a complete course.membership.snapshot.v2 fact.
CREATE TABLE IF NOT EXISTS learning_course_membership_watermark (
    course_id BIGINT NOT NULL PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_learning_roster_watermark_version (snapshot_version)
);
