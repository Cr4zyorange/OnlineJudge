ALTER TABLE lab_experiment
    ADD COLUMN published_at DATETIME NULL AFTER created_by;
