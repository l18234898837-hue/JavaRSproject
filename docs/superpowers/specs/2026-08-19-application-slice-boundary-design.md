# Application Slice Boundary Design

**Status:** Approved direction pending document review

## Context

The original Task 3 combined product preflight, batch/task creation, task detail,
stage dispatch, worker events, cancellation, retry, and expired-stage recovery.
The current domain model and output ports can support a pure-Java creation and
query slice, but do not yet contain the persistence or worker-event contracts
needed to implement reliable execution orchestration.

`ProcessingBatch` exists as a domain object, while no batch repository or
identifier allocation port exists. `TaskStage` does not yet contain execution
leases, event sequence state, or dependency persistence. Implementing dispatch,
callback acceptance, and recovery before those boundaries would create
in-memory behavior that cannot meet the architecture's durable-state rules.

## Decision

Split the original Task 3 into two ordered application slices.

### Slice A: Preflight, Batch/Task Creation, and Task Detail

Implement this slice before database migration and worker execution support.

Responsibilities:

- `PreflightProductUseCase` asks `ProductInspector` to inspect all supplied
  assets and returns a preflight result containing every `ProductCandidate`.
- The worker-facing inspection response is converted at the application
  boundary. The application layer exposes a candidate list, not a worker DTO.
- `CreateProcessingBatchUseCase` creates one `ProcessingBatch` and one child
  `ProcessingTask` per selected candidate.
- `CreateProcessingTaskUseCase` creates a single-task batch and one child task
  so every task continues to have an explicit batch relationship.
- Task creation accepts only `ProductSupportStatus.SUPPORTED` candidates.
  `CANDIDATE`, `UNKNOWN_PRODUCT`, `UNSUPPORTED`, and `DEPRECATED` candidates
  remain eligible for preflight display but are rejected before plan creation.
- Task creation obtains identifiers through output ports, requests a
  read-only asset grant, obtains a `ProcessingPlan` from `ProcessingPlanner`,
  persists the task, and materializes one pending `TaskStage` for each plan
  stage.
- `GetTaskDetailUseCase` verifies that the caller owns the task and assembles
  the task, its stages, and its registered artifacts into an immutable view.

New output ports in Slice A:

- `ProcessingBatchRepository`
- `TaskIdGenerator`
- `ProcessingBatchIdGenerator`
- `StageIdGenerator`

The existing `AssetStorage.grantPreflightRead(...)` operation will be renamed
to `grantRead(...)`, because both preflight and plan generation require a
read-only asset grant. The operation remains a port and has no storage
implementation in this slice.

The existing worker-facing
`application.port.out.model.InspectionResult` will be replaced with
`InspectionResponse`, containing the Worker rule-package version and an
immutable `List<ProductCandidate>`. This avoids two unrelated types named
`InspectionResult` and satisfies the architecture rule that a directory may
produce multiple candidates.

`PlanRequest` will carry the selected `ProductCandidate` rather than the
worker inspection response. The selected candidate is the stable application
input for building a plan.

### Slice B: Execution Orchestration and Recovery

Move these original Task 3 responsibilities until after database migration,
repository mapping, and the versioned `execute-stage` contract exist:

- `DispatchNextStageUseCase`
- `ReceiveWorkerEventUseCase`
- `RecoverExpiredStageUseCase`
- cancellation dispatch
- retry dispatch

Slice B will add durable stage dependency, execution lease, event sequence,
acceptance-status, and recovery state before accepting any Worker event or
starting an execution. It will use `TaskDispatcher` only after the Worker
Gateway exposes a versioned `execute-stage` contract.

## Data Flow

```text
assets
  -> PreflightProductUseCase
  -> ProductInspector
  -> InspectionResponse(candidates)
  -> PreflightResult(candidates)

selected SUPPORTED candidates
  -> CreateProcessingBatchUseCase
  -> allocate batch/task/stage identifiers
  -> AssetStorage.grantRead
  -> ProcessingPlanner.buildPlan(selected candidate)
  -> ProcessingBatchRepository + ProcessingTaskRepository + TaskStageRepository

task id + caller
  -> GetTaskDetailUseCase
  -> ProcessingTaskRepository + TaskStageRepository + ArtifactRepository
  -> TaskDetailView
```

## Error Handling

- Empty asset or candidate collections are rejected by command/value-object
  validation.
- Task creation rejects a candidate unless its support status is `SUPPORTED`.
- A task detail request for a missing task fails distinctly from a request by a
  non-owner.
- A planner or repository failure propagates to the transactional boundary;
  this slice does not dispatch a Worker process and does not write partial
  execution state.
- No absolute filesystem path, SQL, HTTP call, `ProcessBuilder`, or GDAL API is
  introduced into application services.

## Testing

Use pure Java JUnit tests with in-memory port fakes.

- Preflight test: inspector returns multiple candidates and the service returns
  the complete immutable candidate list.
- Batch creation test: two supported candidates create one batch and two tasks,
  each with its own persisted plan and pending stages.
- Unsafe candidate test: `CANDIDATE` and `UNKNOWN_PRODUCT` are rejected before
  invoking `ProcessingPlanner`.
- Single-task creation test: one supported candidate creates a single-candidate
  batch and a child task.
- Task-detail test: returns persisted stages and artifacts only to the task
  creator; a different user is rejected.

## Non-Goals

This change does not add a database schema, MyBatis mapper, Spring bean,
controller, frontend route, frontend page, Worker process launch, Worker event
callback, lease, cancellation, retry, or recovery behavior.
