# Multisource Admin API Foundation Design

**Status:** Approved for planning on 2026-08-20

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

`SysFile` is currently the only confirmed source. The adapter creates an `AssetReference("SYS_FILE", id)`, snapshots its configured server-side file path as a `StorageUri`, and records size/file-name metadata for the Worker asset manifest. A deterministic SHA-256 checksum is calculated from the server-side file only after the adapter has resolved the asset.

`SysFile` has no owner column. Therefore the initial adapter can prove that the requested file exists but cannot enforce per-file ownership. Endpoint access remains authenticated, and task ownership is still enforced by the domain `AccessSubject` when reading task details. Before multi-user production rollout, the file subsystem must supply a verifiable authorization rule or the system must introduce a dedicated managed-asset source; this limitation is explicit and must not be hidden by the API.

## Layering And Assembly

`htht-admin` contains only:

- request and response DTOs;
- the authenticated current-user to `AccessSubject` adapter;
- `SysFileAssetStorageAdapter` implementing the multisource `AssetStorage` port;
- Spring beans that compose existing MyBatis repositories, Worker gateway, ID generators, and Slice A services;
- MVC controllers that validate, map, and delegate.

The controller does not inspect products, invoke a Python process, access MyBatis mappers, or build file paths. `htht-multisource` remains free of `htht-system` imports.

## Request And Response Semantics

All input lists preserve explicit order. Every input item contains `sourceType`, `sourceId`, `role`, `ordinal`, and `required`; `sourceType` is `SYS_FILE` in this release. Preflight returns the worker rule-package version and candidates. Task and batch creation receive a client-selected candidate snapshot returned by preflight, a task or batch name, parameter JSON, and a server-configured output URI selector. Task creation returns a task ID; batch creation returns a batch ID and child task IDs. Detail returns lifecycle status, quality status, plan, candidate snapshots, stages, and registered artifacts.

`GET /processing-tasks/{taskId}` uses the authenticated user ID to enforce domain ownership. A non-owner receives the same not-found result as an absent task. Request validation failures use the application's normal validation/error envelope.

## Explicit Deferrals

The API does not yet provide task list, batch detail, capabilities, cancellation, retry, stage progress events, artifact query/download, or quality/report endpoints. Those operations depend on new application use cases and full execution orchestration: dispatch, execution IDs, event persistence, state transitions, artifact validation/registration, cancellation, retry, and timeout recovery. They require a separate design and implementation plan.

## Tests

Controller tests use `@WebMvcTest` with mocked use cases and asset adapter boundary checks. Adapter tests use temporary files and mocked `ISysFileService` to prove that only resolved server-side paths are read, unknown IDs fail, non-`SYS_FILE` sources fail, and the checksum/snapshot are stable. Spring context tests prove Slice A uses the production adapter and all required beans are available without a database connection. No test writes to application business tables.
