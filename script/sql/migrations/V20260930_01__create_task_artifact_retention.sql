-- Persist user-directed retention state independently from legacy plan retirement.
CREATE TABLE rs_task_artifact_retention (
    task_id bigint PRIMARY KEY REFERENCES rs_processing_task(task_id),
    retention_status varchar(32) NOT NULL,
    pinned boolean NOT NULL DEFAULT false,
    recycled_at timestamptz,
    purge_started_at timestamptz,
    purged_at timestamptz,
    operator_id bigint,
    failure_reason varchar(1024),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rs_task_artifact_retention_status CHECK (
        retention_status IN ('AVAILABLE', 'PINNED', 'RECYCLE_BIN', 'PURGING', 'PURGE_FAILED', 'PURGED', 'MISSING')
    ),
    CONSTRAINT ck_rs_task_artifact_retention_version CHECK (version >= 0),
    CONSTRAINT ck_rs_task_artifact_retention_pinned CHECK (
        (retention_status = 'PINNED' AND pinned = true)
        OR (retention_status <> 'PINNED' AND pinned = false)
    )
);

CREATE INDEX idx_rs_processing_task_creator_updated ON rs_processing_task (creator_id, updated_at DESC);
CREATE INDEX idx_rs_task_product_product_code ON rs_task_product (product_code);
CREATE INDEX idx_rs_task_artifact_retention_status_cleanup
    ON rs_task_artifact_retention (retention_status, purge_started_at);
