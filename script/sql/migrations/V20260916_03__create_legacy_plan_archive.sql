-- Preserve historical plan-v1/plan-v2 tasks while preventing execution after retirement.
CREATE TABLE rs_legacy_plan_archive (
    task_id bigint PRIMARY KEY REFERENCES rs_processing_task(task_id),
    plan_version varchar(32) NOT NULL,
    archived_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason varchar(255) NOT NULL,
    CONSTRAINT ck_rs_legacy_plan_archive_version CHECK (plan_version IN ('plan-v1', 'plan-v2'))
);
