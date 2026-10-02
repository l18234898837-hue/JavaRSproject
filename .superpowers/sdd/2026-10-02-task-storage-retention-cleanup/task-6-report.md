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
