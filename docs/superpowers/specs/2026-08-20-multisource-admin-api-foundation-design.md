# Multisource Admin API Foundation Design

**Status:** Approved for implementation on 2026-08-20

## Goal

Expose the completed multisource Slice A backend through authenticated Admin HTTP APIs without adding frontend work or claiming execution-orchestration behavior that does not exist yet.

## Scope

The first API slice provides these operations:

- `POST /api/multisource/processing-tasks/preflight`
- `POST /api/multisource/processing-tasks`
- `POST /api/multisource/processing-batches`
- `GET /api/multisource/processing-tasks/{taskId}`

It receives only existing `sys_file` identifiers as input assets. The API body carries `sourceType`, but this release accepts only `SYS_FILE`; all other values return a validation error. The Admin adapter resolves each identifier through `ISysFileService`, creates immutable `InputAsset` snapshots, and never accepts a file-system path, Worker command, Python environment, or storage URI from the client.

## Asset Boundary

`SysFile` is currently the only confirmed source. `SysFileAssetStorageAdapter` is also the Admin-side input mapper: it creates `AssetReference("SYS_FILE", id)`, the logical `StorageUri("rs://sys-file/{id}")`, and a SHA-256 checksum calculated from the resolved server-side file. `LocalWorkerAssetManifestWriter` calls the same adapter as a `LocalWorkerAssetResolver`; it resolves the logical URI back to `sys_file.filePath` and rejects the file unless its real path is a readable regular file below `${file.upload-path}`. Each read or execution grant also records its allowed logical URI set and expiry; resolver calls reject an unknown, expired, or out-of-scope grant. The existing upload root is therefore the first Worker `assetRoot`; no large-file copy is introduced in this slice.

`SysFile` has no owner column. Therefore the initial adapter can prove that the requested file exists but cannot enforce per-file ownership. Endpoint access remains authenticated, and task ownership is still enforced by the domain `AccessSubject` when reading task details. Before multi-user production rollout, the file subsystem must supply a verifiable authorization rule or the system must introduce a dedicated managed-asset source; this limitation is explicit and must not be hidden by the API.

## Layering And Assembly

`htht-admin` contains only:

- request and response DTOs;
- the authenticated current-user to `AccessSubject` adapter;
- `SysFileAssetStorageAdapter` implementing the multisource `AssetStorage` port and `LocalWorkerAssetResolver`;
- Spring beans that compose existing MyBatis repositories, Worker gateway, ID generators, and Slice A services;
- MVC controllers that validate, map, and delegate.

The controller does not inspect products, invoke a Python process, access MyBatis mappers, or build file paths. `htht-multisource` remains free of `htht-system` imports.

## Request And Response Semantics

All input lists preserve explicit order. Every input item contains `sourceType`, `sourceId`, `role`, `ordinal`, and `required`; `sourceType` is `SYS_FILE` in this release. Preflight returns the worker rule-package version, an opaque `preflightToken`, and candidate summaries. Task and batch creation receive the token, a server-issued candidate ID, a task or batch name, and parameter JSON; they do not accept a client-supplied candidate snapshot or output URI. The server retrieves the immutable candidate snapshot captured by preflight and consumes the token only after successful creation. Task creation returns a task ID; batch creation returns a batch ID and child task IDs. Detail returns lifecycle status, quality status, plan, candidate snapshots, stages, and registered artifacts.

### Preflight Session Boundary

The initial implementation uses an in-memory `PreflightSessionStore` behind a multisource application port. A session records the authenticated user ID, the ordered resolved `InputAsset` snapshots, the candidate list, a random opaque token, an expiry time of 30 minutes, and whether the session has been consumed. Creation requires that the current authenticated user matches the session owner, the token is unexpired, and the selected candidate ID belongs to that session. The service consumes a session atomically only after it has created its task or batch successfully; failed validation or persistence does not destroy the user's valid selection.

The in-memory store is suitable for the initial single-instance Admin deployment but does not survive restart and is not shared across nodes. Its port permits a later Redis or `rs_`-table implementation without changing the HTTP contract. Tokens do not substitute for `sys_file` ownership checks: `sys_file` still lacks verifiable owner information, which remains an explicit limitation.

`GET /processing-tasks/{taskId}` uses the authenticated user ID to enforce domain ownership. A non-owner receives the same not-found result as an absent task. Request validation failures use the application's normal validation/error envelope. Worker command, work root, timeout, and `${file.upload-path}` are server configuration only; none is accepted in a request body.

## Explicit Deferrals

The API does not yet provide task list, batch detail, capabilities, cancellation, retry, stage progress events, artifact query/download, or quality/report endpoints. Those operations depend on new application use cases and full execution orchestration: dispatch, execution IDs, event persistence, state transitions, artifact validation/registration, cancellation, retry, and timeout recovery. They require a separate design and implementation plan.

## Tests

Controller tests use `@WebMvcTest` with mocked use cases and asset adapter boundary checks. Adapter tests use temporary files and mocked `ISysFileService` to prove that only resolved server-side paths are read, unknown IDs fail, non-`SYS_FILE` sources fail, and the checksum/snapshot are stable. Spring context tests prove Slice A uses the production adapter and all required beans are available without a database connection. No test writes to application business tables.
