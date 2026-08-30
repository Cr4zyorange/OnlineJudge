-- A mandatory publish can be broker-confirmed but returned as unroutable. Keep that fact durable
-- so an operator can add the missing binding and replay the same event id safely.
ALTER TABLE assessment_event_outbox ADD COLUMN IF NOT EXISTS delivery_attempt INT NOT NULL DEFAULT 0;
ALTER TABLE assessment_event_outbox ADD COLUMN IF NOT EXISTS last_error VARCHAR(1024) NULL;
