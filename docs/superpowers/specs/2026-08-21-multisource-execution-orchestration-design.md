# Multisource Execution Orchestration Design

**Status:** Approved for implementation on 2026-08-21

## Goal

Complete the backend execution path after task creation: claim runnable DAG stages, invoke the configured Worker, persist idempotent execution events, advance dependent stages, and expose reliable task progress and results without adding frontend work.

## Scope

This slice covers:

- dispatching the next dependency-satisfied `PENDING` stage;
- server-generated `executionId` and per-attempt execution grant;
- Worker completion, failure, cancellation, and timeout handling;
- idempotent event acceptance and stage state transitions;
- artifact validation and registration after successful execution;
- automatic progression of dependent stages and task lifecycle summary;
- backend APIs for dispatch, cancel, retry, progress, events, artifacts, and quality results where existing HTTP conventions permit.

It does not change frontend code, frontend contracts outside these backend endpoints, or the confirmed five product adapters. Database migrations are prepared and tested against an isolated schema only; the development database is not modified during implementation.

## Architecture

`DispatchNextStageUseCase` generates an `ExecutionId`, constructs a lease-bound execution context, and passes both to `TaskStageRepository` to atomically claim a runnable stage. The claim transitions that stage to `RUNNING` in the same compare-and-set operation, then the use case creates a grant, builds a server-owned `StageExecutionRequest`, and calls `TaskDispatcher`. The current implementation uses the existing local Worker gateway synchronously, while the application boundary is event-oriented so the gateway can later be replaced by a queue or remote Worker.

The current local Worker is synchronous and returns a structured terminal response (`workerId`, `status`, and `artifacts`), not asynchronous lifecycle callbacks. The placeholder Worker currently returns `status=SUCCEEDED` with an empty artifact list because it does not yet execute a real algorithm. Therefore the Java dispatcher records its own terminal event with `eventSequence=1` for each attempt: success after a validated synchronous response, or `FAILED` with a structured dispatch error when the gateway throws. The existing database uniqueness key `(stageId, attempt, eventSequence)` is the first-version idempotency key. A future remote Worker callback may provide additional event sequences for the same attempt, but an external Worker event ID is not part of the current contract.

## State Rules

The valid stage transitions are:

```text
PENDING -> RUNNING -> SUCCEEDED
PENDING -> RUNNING -> FAILED
RUNNING -> RECOVERY_PENDING   (lease/timeout recovery)
RECOVERY_PENDING -> RETRYING  (explicit retry policy)
RETRYING -> RUNNING
PENDING -> SKIPPED            (dependency or task cancellation)
RUNNING -> SKIPPED            (cancellation acknowledged)
```

An attempt increments only when a stage is explicitly retried after a terminal failure or recovered timeout. A dispatch claim does not increment the attempt. `TaskStage` retains its existing identity, key, status, and primitive `attempt`, and gains an optional immutable `StageExecution` context containing execution ID, lease, Worker ID, failure detail, and start/finish times. The Worker ID is nullable during the short interval between the atomic claim and the synchronous Worker receipt; it is not an identity or a concurrency key. Only the current `executionId` and attempt may mutate a `RUNNING` stage. A stage can be marked `SUCCEEDED` once; later duplicate success events are no-ops.

The task becomes `RUNNING` when its first stage is claimed, `SUCCEEDED` when all required stages succeed, `FAILED` when a non-retryable required stage fails, `CANCELLED` when cancellation has skipped or stopped all remaining work, and remains `CREATED` only before any dispatch. Quality status remains `PENDING` until the quality stage/result is registered.

## DAG Progression

The planner snapshot remains the source of stage order and dependencies. Task creation combines the Worker-returned plan version and stages with the server deployment's configured `processorVersion`, then persists the resulting immutable plan snapshot. A stage is runnable only when all required predecessor stages are `SUCCEEDED`. On success, the transaction registers validated artifacts, marks the current stage complete, and identifies newly runnable stages. Dispatch of the next stage occurs after the state transaction commits, preventing a Worker callback from observing a partially persisted graph. No client-provided stage type, dependency, or Worker version may override the task plan snapshot.

## Execution Grant And Output Safety

The server creates an execution grant containing task ID, stage ID, execution ID, attempt, allowed input logical URIs, output logical URI, and expiry. The grant reference is opaque to the Worker; its full scope remains server-side and is validated by the asset resolver. The Worker receives only the reference and expiry through the existing local manifest/contract, never an unrestricted client path. Output files must be regular files under the controlled Worker work root, have a declared logical URI and checksum, and pass the stage's expected artifact contract before registration.

## Failure, Cancellation, And Recovery

Worker protocol errors, non-zero exits, invalid responses, missing outputs, checksum mismatches, and expired grants fail the current attempt with a structured reason. Timeout moves a running stage to `RECOVERY_PENDING`; a recovery operation may retry it according to the configured maximum attempts. Cancellation records the request, asks the dispatcher to stop the active execution, and prevents new stages from being claimed. A late Worker event cannot resurrect a cancelled or superseded attempt.

## Persistence And Concurrency

The execution event table stores event ID, execution ID, task/stage IDs, attempt, event type, payload snapshot, received time, and applied/ignored result. A unique constraint on the event idempotency key makes duplicate delivery safe. Stage claiming uses one database transaction with row-level locking or an equivalent compare-and-set update. State transition, event insert, artifact registration, and task summary update share one transaction; Worker process invocation is outside that transaction.

## API Shape

The backend adds authenticated task operations for:

- dispatching the next runnable stage or requesting dispatch of a specific task;
- cancelling a task;
- retrying a failed or recovered stage;
- reading progress and execution events;
- reading registered artifacts and quality results.

Client requests identify tasks and optional retry reasons only. The server owns stage IDs, execution IDs, grants, input paths, output locations, worker commands, and plan snapshots. Non-owners receive the existing not-found behavior.

## Testing And Verification

Tests are layered and focused:

1. Domain transition tests cover every valid and invalid state transition.
2. Application tests cover atomic claim, dependency filtering, duplicate events, stale events, retry limits, cancellation, and task summary updates with in-memory ports.
3. Persistence tests verify the unique event key and compare-and-set/locking behavior against an isolated PostgreSQL schema when available.
4. Worker gateway tests verify timeout, malformed response, output validation, and cancellation translation.
5. Controller tests verify ownership, request validation, and response mapping without writing the development database.

The first implementation checkpoint is the in-memory dispatch boundary plus idempotent event acceptance and stage transitions. The current checkpoint does not yet persist execution state through MyBatis, register validated artifacts, or implement cancellation/recovery. Those behaviors remain separate checkpoints, each with its own focused verification.

## Explicit Non-Goals

- frontend implementation or frontend-backend integration;
- production queue, distributed scheduler, or multi-node lease service;
- changing existing source tables such as `st_island_info` or `sd_observe_data`;
- using physical paths or client-supplied environment parameters as API contracts.
