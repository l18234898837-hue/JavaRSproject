# Source and Artifact Recycle Bin Design

## Goal

Give source uploads and task artifacts independent recycle-bin lifecycles. Batch actions move resources to their corresponding recycle bin; workspace-specific expiry workers physically purge expired resources; users can inspect metadata, restore resources, or permanently delete them after explicit confirmation.

## Boundaries

- Source-input retention and artifact retention are separate aggregates and never share status, expiry, or cleanup timestamps.
- Both recycle bins default to 14 days, with independent workspace-level policy fields.
- Moving to a recycle bin starts a new expiry clock. Restoring returns the same resource to `AVAILABLE`, clears recycle-bin timestamps, and restarts the normal retention policy.
- Permanent deletion is asynchronous and preserves task/resource metadata. A failed physical purge remains retryable as `PURGE_FAILED`.
- Existing task ownership and terminal-task checks remain mandatory for every user operation.

## Domain Model

`TaskInputRetentionStatus` gains `RECYCLE_BIN`. `TaskInputRetention` gains `recycledAt` and `recycleExpiresAt`; source transitions are `AVAILABLE -> RECYCLE_BIN -> AVAILABLE` or `RECYCLE_BIN -> PURGING -> PURGED/PURGE_FAILED`.

`TaskArtifactRetention` gains `recycleExpiresAt`; artifact transitions remain `AVAILABLE/PINNED -> RECYCLE_BIN -> AVAILABLE` or `RECYCLE_BIN -> PURGING -> PURGED/PURGE_FAILED`.

`WorkspaceStoragePolicyRepository.Policy` gains `sourceRecycleBinRetentionDays` and `artifactRecycleBinRetentionDays`, both defaulting to 14 and validated independently.

## API Surface

- Batch source input operation `RECYCLE`; batch artifact operation `RECYCLE` remains independent.
- Single-task source retention supports `RECYCLE`, `RESTORE`, and explicit `PURGE` confirmation.
- Existing artifact retention supports `RECYCLE` and `RESTORE`; explicit artifact purge remains confirmation-gated.
- Existing history API filters (`inputRetention=RECYCLE_BIN` or `retention=RECYCLE_BIN`) provide the recycle-bin listing; no unified recycle-bin resource API is added in the MVP.
- `POST /api/multisource/processing-tasks/{taskId}/input-retention` accepts `RECYCLE` and `RESTORE`.
- `POST /api/multisource/processing-tasks/{taskId}/artifact-retention` accepts `RECYCLE` and `RESTORE`.
- `POST /api/multisource/processing-tasks/{taskId}/input-purge` and existing artifact purge remain explicit-confirmation endpoints.

## Cleanup

Source and artifact cleanup jobs claim only their own repository rows using version-qualified optimistic locking. Source cleanup deletes upload/input directories only; artifact cleanup deletes worker output, ZIP caches, TIFF tiles, and PNG previews only. Both jobs are idempotent and retain metadata on failure.

## Frontend

The existing history dialog gains a source/artifact recycle-bin tab or filter mode. It has separate `批量移入源文件回收站` and `批量移入产物回收站` actions with independent preflight checks. The existing detail area provides metadata, restore, and permanent-delete actions. No separate dialog component is added in the MVP. Permanent delete uses a second confirmation explicitly stating that the selected resource will be physically deleted and cannot be recovered.

## Verification

Cover domain transitions, policy defaults/bounds, optimistic-lock claims, worker expiry selection, ownership checks, controller contracts, batch preflight, recycle-bin rendering, confirmation text, and independent source/artifact state handling. Existing history/download/preview tests must remain green.
