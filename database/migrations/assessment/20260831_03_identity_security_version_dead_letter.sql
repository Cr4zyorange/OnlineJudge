-- Upgrade already-created Assessment schemas without requiring the initial
-- service migration to be reapplied. Terminal Identity envelopes must remain
-- repairable after a worker restart or a deployment of this version.
CREATE TABLE IF NOT EXISTS assessment_identity_security_version_dead_letter (
  event_id VARCHAR(80) PRIMARY KEY,
  correlation_id VARCHAR(80) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  failure_reason VARCHAR(256) NOT NULL,
  delivery_attempt INT NOT NULL DEFAULT 1,
  replay_count INT NOT NULL DEFAULT 0,
  received_at TIMESTAMP NOT NULL,
  replayed_at TIMESTAMP NULL
);
