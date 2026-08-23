-- Persist the execution identity needed to audit and reject stale Worker events.
-- Rollback: ALTER TABLE rs_task_event DROP COLUMN execution_id;

ALTER TABLE rs_task_event ADD COLUMN execution_id varchar(128);
