-- Purpose: add query indexes for multisource lifecycle and execution lookups.
-- Dependency: V20260819_01__create_multisource_core_tables.sql.
-- Rollback: drop each index by its name.

CREATE INDEX idx_rs_processing_task_batch_id ON rs_processing_task(batch_id);
CREATE INDEX idx_rs_processing_task_lifecycle ON rs_processing_task(lifecycle_status, updated_at DESC);
CREATE INDEX idx_rs_task_stage_task_id ON rs_task_stage(task_id);
CREATE INDEX idx_rs_task_stage_lease ON rs_task_stage(execution_status, lease_expire_at)
    WHERE execution_status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_rs_task_event_stage_id ON rs_task_event(stage_id, recorded_at DESC);
CREATE INDEX idx_rs_artifact_task_id ON rs_artifact(task_id, created_at DESC);
CREATE INDEX idx_rs_product_profile_status ON rs_product_profile(support_status, product_code, profile_version);
CREATE INDEX idx_rs_profile_capability_code ON rs_profile_capability(capability_code);
