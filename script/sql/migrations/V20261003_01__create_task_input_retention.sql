-- Durable source-input retention state. Task/artifact metadata stays intact after purge.
CREATE TABLE rs_task_input_retention (
    task_id bigint PRIMARY KEY REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    workspace_key varchar(128) NOT NULL,
    retention_status varchar(32) NOT NULL DEFAULT 'AVAILABLE',
    expires_at timestamptz,
    scheduled_at timestamptz,
    purge_started_at timestamptz,
    purged_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator_id bigint,
    failure_reason varchar(1024),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_rs_task_input_retention_version CHECK (version >= 0)
);

CREATE INDEX idx_rs_task_input_retention_status
    ON rs_task_input_retention (workspace_key, retention_status, expires_at);
