ALTER TABLE rs_task_input_retention
    ADD COLUMN recycled_at timestamptz,
    ADD COLUMN recycle_expires_at timestamptz;

ALTER TABLE rs_task_artifact_retention
    ADD COLUMN recycle_expires_at timestamptz;

ALTER TABLE rs_workspace_storage_policy
    ADD COLUMN source_recycle_bin_retention_days integer NOT NULL DEFAULT 14,
    ADD COLUMN artifact_recycle_bin_retention_days integer NOT NULL DEFAULT 14;

CREATE INDEX idx_rs_task_input_retention_recycle_cleanup
    ON rs_task_input_retention (retention_status, recycle_expires_at);

CREATE INDEX idx_rs_task_artifact_retention_recycle_cleanup
    ON rs_task_artifact_retention (retention_status, recycle_expires_at);
