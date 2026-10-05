# Task 6 report

## Implemented

- Added independent source-input retention fields to history rows/details while preserving the existing artifact `retentionStatus` field and constructor compatibility.
- Added source retention operations (`PIN`, `SET_EXPIRY`, `PURGE`) and source-only batch operations.
- Added artifact-only batch retention operations (`PIN`, `RECYCLE`, `RESTORE`); permanent purge remains the existing individually confirmed endpoint.
- Added workspace storage policy DTOs and version-checked `GET/PATCH /processing-storage/policy` endpoints.
- Added filesystem-backed storage summary endpoint covering source, artifact, Worker, ZIP, reclaimable, and cleanup-failure counters.
- Added TypeScript source-retention, batch, storage-summary, and workspace-policy types/API helpers in `rsimageprocess-view/src/api/multiSourceSatellite.ts`.
- Preserved the legacy seven-argument `TaskHistoryController.history` Java call shape in commit `eff0b38`.

## Verification

- `pnpm typecheck`: passed.
- `pnpm test:multisource -- --runInBand`: passed (272 tests).
- `git diff --check`: passed for the Java worktree changes.
- `mvn -pl htht-admin -DskipTests compile`: blocked before compilation because Maven dependencies could not be downloaded in the restricted environment (`Permission denied: getsockopt`).

## Notes

- Existing source-retention state is still provided by the Task 3 application service; the history SQL fallback is `AVAILABLE` until a persisted source-retention projection is introduced.
- Existing unrelated worker changes and the implementation plan file were left untouched.

## Review fixes

- Added a durable `rs_task_input_retention` projection, MyBatis repository, and CAS persistence wiring. The production source-retention service now reads/writes that repository; its in-memory implementation is retained only by test-only compatibility constructors.
- History list/count SQL now joins and filters the persisted source projection before pagination. Missing legacy rows intentionally read as `AVAILABLE`.
- Source retention operations resolve the workspace from the owned processing task instead of accepting a client-selected workspace. Source purge receives that same trusted workspace.
- Global storage statistics and policy endpoints now require `LoginHelper.isAdmin()`. The endpoint uses a global database aggregate consistently with its configured-root filesystem scan.
- Storage categories are mutually exclusive: archive ZIP files are read from `workRoot/archives`; Worker bytes exclude that subtree.
- Policy PATCH requires `expectedVersion`; absent and stale values are rejected, and stale races return HTTP 409.
- Added focused controller/migration/service tests for category accounting, admin rejection, required/stale policy versions, independent source filter forwarding, durable service replacement, and the migration projection.

## Source input metrics follow-up

- `GET /processing-tasks/{taskId}/history-detail` now returns `sourceInputCount` and `sourceInputSizeBytes` independently from `artifactSizeBytes`.
- Source metrics aggregate only `ACTIVE` task input references joined to managed input files; released references are excluded, so a completed source purge reports zero source inputs and zero source bytes.
- The detail controller obtains the task through the existing owner-scoped detail use case before reading input metrics.

## Follow-up verification

- `git diff --check`: passed.
- `mvn -pl htht-admin -Dtest=TaskHistoryControllerTest test`: blocked before test compilation because the restricted environment cannot download the absent `spring-boot-dependencies:2.7.13` and `hutool-bom:5.8.18` parent BOMs (`Permission denied: getsockopt`).
