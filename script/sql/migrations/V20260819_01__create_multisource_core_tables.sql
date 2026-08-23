-- Purpose: create the durable multisource processing schema.
-- Dependency: PostgreSQL 14+ with the current deployment search_path selected.
-- Rollback: drop the rs_ tables in reverse dependency order in a controlled deployment.

CREATE TABLE rs_schema_migration (
    version varchar(32) PRIMARY KEY,
    filename varchar(255) NOT NULL UNIQUE,
    sha256 char(64) NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rs_processing_batch (
    batch_id bigint PRIMARY KEY,
    name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rs_batch_product (
    batch_product_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id bigint NOT NULL REFERENCES rs_processing_batch(batch_id) ON DELETE CASCADE,
    package_id varchar(255) NOT NULL,
    product_code varchar(128) NOT NULL,
    profile_version varchar(64) NOT NULL,
    data_shape varchar(64) NOT NULL,
    support_status varchar(32) NOT NULL,
    confidence numeric(5,4) NOT NULL,
    input_assets_snapshot jsonb NOT NULL,
    UNIQUE (batch_id, package_id),
    CHECK (jsonb_typeof(input_assets_snapshot) = 'array')
);

CREATE TABLE rs_processing_task (
    task_id bigint PRIMARY KEY,
    batch_id bigint NOT NULL REFERENCES rs_processing_batch(batch_id),
    task_no varchar(64) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    creator_id bigint NOT NULL,
    lifecycle_status varchar(32) NOT NULL,
    quality_status varchar(32) NOT NULL,
    parameter_snapshot jsonb NOT NULL,
    output_uri varchar(2048) NOT NULL,
    plan_snapshot jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (jsonb_typeof(parameter_snapshot) = 'object'),
    CHECK (jsonb_typeof(plan_snapshot) = 'object')
);

CREATE TABLE rs_task_product (
    task_product_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    package_id varchar(255) NOT NULL,
    product_code varchar(128) NOT NULL,
    profile_version varchar(64) NOT NULL,
    data_shape varchar(64) NOT NULL,
    support_status varchar(32) NOT NULL,
    confidence numeric(5,4) NOT NULL,
    UNIQUE (task_id, package_id)
);

CREATE TABLE rs_task_input_asset (
    input_asset_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    package_id varchar(255) NOT NULL,
    role varchar(64) NOT NULL,
    ordinal integer NOT NULL,
    reference_type varchar(64) NOT NULL,
    reference_value varchar(2048) NOT NULL,
    storage_uri_snapshot varchar(2048) NOT NULL,
    checksum varchar(255) NOT NULL,
    required boolean NOT NULL,
    UNIQUE (task_id, package_id, role, ordinal),
    FOREIGN KEY (task_id, package_id) REFERENCES rs_task_product(task_id, package_id),
    CHECK (ordinal >= 0)
);

CREATE TABLE rs_task_stage (
    stage_id bigint PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    stage_key varchar(128) NOT NULL,
    execution_status varchar(32) NOT NULL,
    attempt integer NOT NULL,
    execution_id varchar(128),
    worker_id varchar(128),
    lease_expire_at timestamptz,
    heartbeat_at timestamptz,
    error_code varchar(128),
    error_message text,
    log_uri varchar(2048),
    started_at timestamptz,
    finished_at timestamptz,
    UNIQUE (task_id, stage_key),
    CHECK (attempt > 0)
);

CREATE TABLE rs_task_stage_dependency (
    stage_id bigint NOT NULL REFERENCES rs_task_stage(stage_id) ON DELETE CASCADE,
    depends_on_stage_id bigint NOT NULL REFERENCES rs_task_stage(stage_id) ON DELETE CASCADE,
    PRIMARY KEY (stage_id, depends_on_stage_id),
    CHECK (stage_id <> depends_on_stage_id)
);

CREATE TABLE rs_task_event (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stage_id bigint NOT NULL REFERENCES rs_task_stage(stage_id) ON DELETE CASCADE,
    attempt integer NOT NULL,
    event_sequence bigint NOT NULL,
    event_type varchar(64) NOT NULL,
    acceptance_status varchar(32) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (stage_id, attempt, event_sequence),
    CHECK (attempt > 0)
);

CREATE TABLE rs_artifact (
    artifact_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    stage_id bigint REFERENCES rs_task_stage(stage_id) ON DELETE SET NULL,
    artifact_type varchar(64) NOT NULL,
    storage_uri varchar(2048) NOT NULL,
    checksum varchar(255) NOT NULL,
    size_bytes bigint,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE TABLE rs_quality_result (
    quality_result_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES rs_processing_task(task_id) ON DELETE CASCADE,
    artifact_id bigint REFERENCES rs_artifact(artifact_id) ON DELETE SET NULL,
    quality_status varchar(32) NOT NULL,
    rule_code varchar(128) NOT NULL,
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    checked_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rs_product_profile (
    product_code varchar(128) NOT NULL,
    profile_version varchar(64) NOT NULL,
    display_name varchar(255) NOT NULL,
    data_shape varchar(64) NOT NULL,
    support_status varchar(32) NOT NULL,
    published_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_code, profile_version)
);

CREATE TABLE rs_processing_capability (
    capability_code varchar(128) PRIMARY KEY,
    capability_version varchar(64) NOT NULL,
    display_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    definition jsonb NOT NULL DEFAULT '{}'::jsonb,
    published_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rs_profile_capability (
    product_code varchar(128) NOT NULL,
    profile_version varchar(64) NOT NULL,
    capability_code varchar(128) NOT NULL REFERENCES rs_processing_capability(capability_code),
    PRIMARY KEY (product_code, profile_version, capability_code),
    FOREIGN KEY (product_code, profile_version)
        REFERENCES rs_product_profile(product_code, profile_version) ON DELETE CASCADE
);
