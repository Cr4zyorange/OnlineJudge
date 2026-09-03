-- The V01 compatibility shape retained `status`; V02 introduced the canonical
-- `source_status` field but left its non-null predecessor in place.  Runtime
-- projections exclusively write `source_status`, so the legacy column makes a
-- valid source-grade event fail under MySQL strict mode.
ALTER TABLE grade_source_projection
    DROP COLUMN status;
