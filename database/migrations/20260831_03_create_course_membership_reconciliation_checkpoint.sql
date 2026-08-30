-- #337 P0: Course, not Learning, owns the durable periodic recovery trigger
-- for a lost Course-member projection.  One row per Course records the
-- latest complete snapshot and suppresses concurrent/repeated re-snapshots
-- until the configured reconciliation interval is due.
CREATE TABLE IF NOT EXISTS course_membership_reconciliation_checkpoint (
    course_id BIGINT NOT NULL PRIMARY KEY,
    snapshot_event_id VARCHAR(64) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    next_reconcile_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_course_membership_reconciliation_due (next_reconcile_at)
);
