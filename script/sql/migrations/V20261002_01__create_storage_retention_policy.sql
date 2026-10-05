-- Add workspace-scoped source retention state without removing existing task or file data.
ALTER TABLE rs_processing_task
    ADD COLUMN workspace_key varchar(128) NOT NULL DEFAULT 'default';

UPDATE rs_processing_task
SET workspace_key = 'default'
WHERE workspace_key IS NULL;

CREATE TABLE rs_workspace_storage_policy (
    workspace_key varchar(128) PRIMARY KEY,
    input_retention_days integer NOT NULL DEFAULT 14,
    orphan_upload_ttl_hours integer NOT NULL DEFAULT 24,
    worker_directory_ttl_hours integer NOT NULL DEFAULT 48,
    auto_cleanup_enabled boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rs_workspace_storage_policy_input_retention CHECK (input_retention_days >= 0),
    CONSTRAINT ck_rs_workspace_storage_policy_orphan_ttl CHECK (orphan_upload_ttl_hours >= 24),
    CONSTRAINT ck_rs_workspace_storage_policy_worker_ttl CHECK (worker_directory_ttl_hours >= 1),
    CONSTRAINT ck_rs_workspace_storage_policy_version CHECK (version >= 0)
);

CREATE TABLE rs_managed_input_file (
    managed_file_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_key varchar(128) NOT NULL,
    sys_file_id bigint,
    file_path_snapshot varchar(2048) NOT NULL,
    checksum varchar(255) NOT NULL,
    size_bytes bigint,
    status varchar(32) NOT NULL DEFAULT 'MANAGED',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_rs_managed_input_file_workspace_sys UNIQUE (workspace_key, sys_file_id),
    CONSTRAINT fk_rs_managed_input_file_workspace
        FOREIGN KEY (workspace_key) REFERENCES rs_workspace_storage_policy(workspace_key),
    CONSTRAINT ck_rs_managed_input_file_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT ck_rs_managed_input_file_version CHECK (version >= 0)
);

CREATE TABLE rs_task_input_reference (
    reference_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    managed_file_id bigint NOT NULL REFERENCES rs_managed_input_file(managed_file_id),
    source_type varchar(64) NOT NULL,
    source_id varchar(255) NOT NULL,
    checksum varchar(255) NOT NULL,
    reference_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_rs_task_input_reference_task_file UNIQUE (task_id, managed_file_id),
    CONSTRAINT ck_rs_task_input_reference_version CHECK (version >= 0)
);

CREATE INDEX idx_rs_processing_task_workspace ON rs_processing_task (workspace_key, task_id);
CREATE INDEX idx_rs_managed_input_file_status ON rs_managed_input_file (workspace_key, status);
CREATE INDEX idx_rs_task_input_reference_status ON rs_task_input_reference (task_id, reference_status);

INSERT INTO rs_workspace_storage_policy (workspace_key)
VALUES ('default')
ON CONFLICT (workspace_key) DO NOTHING;
