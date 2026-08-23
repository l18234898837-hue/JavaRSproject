-- Purpose: persist immutable product metadata snapshots for batches and tasks.
-- Dependency: V20260819_01__create_multisource_core_tables.sql.
-- Rollback: drop the two metadata_snapshot columns and their checks in a controlled deployment.

ALTER TABLE rs_batch_product ADD COLUMN metadata_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE rs_batch_product ADD CONSTRAINT chk_rs_batch_product_metadata_snapshot_object
    CHECK (jsonb_typeof(metadata_snapshot) = 'object');

ALTER TABLE rs_task_product ADD COLUMN metadata_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE rs_task_product ADD CONSTRAINT chk_rs_task_product_metadata_snapshot_object
    CHECK (jsonb_typeof(metadata_snapshot) = 'object');
