-- Read-only retirement gate for plan-v1/plan-v2.
-- Run against the target PostgreSQL database before and after archival.

SELECT plan_snapshot->>'version' AS plan_version,
       lifecycle_status,
       count(*) AS task_count
FROM rs_processing_task
GROUP BY 1, 2
ORDER BY 1, 2;

SELECT t.task_id,
       t.task_no,
       t.lifecycle_status,
       t.plan_snapshot->>'version' AS plan_version,
       s.stage_id,
       s.stage_key,
       s.execution_status,
       s.attempt,
       s.execution_id,
       s.lease_expire_at,
       (s.lease_expire_at IS NOT NULL AND s.lease_expire_at < CURRENT_TIMESTAMP) AS lease_expired
FROM rs_processing_task t
JOIN rs_task_stage s ON s.task_id = t.task_id
WHERE t.plan_snapshot->>'version' IN ('plan-v1', 'plan-v2')
  AND (t.lifecycle_status IN ('CREATED', 'QUEUED', 'RUNNING', 'RECOVERY_PENDING', 'CANCELING')
       OR s.execution_status IN ('RUNNING', 'RETRYING', 'RECOVERY_PENDING'))
ORDER BY t.task_id, s.stage_id;

SELECT t.plan_snapshot->>'version' AS plan_version,
       count(DISTINCT t.task_id) AS task_count,
       count(a.artifact_id) AS artifact_count
FROM rs_processing_task t
LEFT JOIN rs_artifact a ON a.task_id = t.task_id
WHERE t.plan_snapshot->>'version' IN ('plan-v1', 'plan-v2')
GROUP BY 1
ORDER BY 1;

-- The retirement gate passes only when this returns zero rows.
SELECT t.task_id,
       t.plan_snapshot->>'version' AS plan_version,
       t.lifecycle_status,
       s.stage_id,
       s.stage_key,
       s.execution_status
FROM rs_processing_task t
JOIN rs_task_stage s ON s.task_id = t.task_id
WHERE t.plan_snapshot->>'version' IN ('plan-v1', 'plan-v2')
  AND (t.lifecycle_status IN ('CREATED', 'QUEUED', 'RUNNING', 'RECOVERY_PENDING', 'CANCELING')
       OR s.execution_status IN ('RUNNING', 'RETRYING', 'RECOVERY_PENDING'));
