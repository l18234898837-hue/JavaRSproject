# plan-v3 Retirement Runbook

## Scope

This runbook retires legacy `plan-v1` and `plan-v2` execution for new code while preserving historical task snapshots and artifacts. It is separate from the implementation plan and is used during the maintenance window.

## Preflight

1. Stop the Java scheduler/worker dispatch loop and record the application build identifier.
2. Run `script/sql/maintenance/20260916_plan_v3_inventory.sql` with a read-only database account.
3. Save all four result sets with the maintenance timestamp.
4. Do not archive while any legacy stage has a non-expired lease.
5. Take a PostgreSQL backup or snapshot before applying the archive migration.

## Archive Gate

The gate is closed when any legacy task (`plan-v1` or `plan-v2`) is in `CREATED`, `QUEUED`, `RUNNING`, `RECOVERY_PENDING`, or `CANCELING`, or when any legacy stage is in `RUNNING`, `RETRYING`, or `RECOVERY_PENDING`. Expired leases still require an explicit cancellation transition.

The gate is open only when the final query in the inventory SQL returns zero rows. Historical terminal tasks may remain in the main tables and must have an entry in `rs_legacy_plan_archive`.

## Postflight

1. Re-run the inventory and store the zero-row gate result.
2. Verify counts and URIs in `rs_artifact` are unchanged.
3. Verify archived task detail, progress, quality report, artifact inspection, artifact download, and result ZIP endpoints.
4. Create one new task for every supported profile and verify `planVersion=plan-v3`.
5. Keep the database snapshot and the previous application/worker binaries until acceptance is complete.

## Rollback

Restore the previous application/worker binaries and database snapshot if a new `plan-v3` task cannot be created or a historical read-only endpoint regresses. Do not delete `rs_legacy_plan_archive`, task snapshots, or artifact files during rollback.
