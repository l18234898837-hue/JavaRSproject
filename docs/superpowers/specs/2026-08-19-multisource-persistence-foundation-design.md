# Multisource Persistence Foundation Design

**Status:** Proposed for implementation

## Purpose

Persist the completed Slice A batch, task, candidate, input-asset, stage, and
artifact state in PostgreSQL without coupling the domain or application layers
to MyBatis. Establish the durable database state required before designing the
deferred execution Slice B.

## Alternatives Considered

1. Put all migration verification in the shared `public` schema and clean rows
   afterward. Rejected because DDL, migration metadata, and a failed cleanup
   could affect the development database.
2. Use one transaction and roll it back after verification. Viable for
   PostgreSQL DDL, but cleanup cannot be independently proven after rollback.
3. Create a unique temporary schema, set its `search_path`, run migrations and
   repository round trips there, then explicitly drop the schema. Selected.
   It keeps `public` untouched and makes both setup and cleanup observable.

## Scope

This slice adds three immutable migration scripts and persistence adapters for
the application ports already used by Slice A:

- `ProcessingBatchRepository`
- `ProcessingTaskRepository`
- `TaskStageRepository`
- `ArtifactRepository`
- task, batch, and stage ID generators

Before adding adapters, the domain snapshots are expanded so a repository can
round-trip all required creation data without depending on a command object.
`ProcessingTask` gains `name`, `parameterSnapshot`, and `outputUri`.
`InputAsset` gains `storageUriSnapshot`. The direct task service passes its
command name to the task; batch children use their batch command name. These
names need not be unique because `task_no` is the durable unique identifier.

It creates the execution-state tables required by the architecture, but does
not add dispatch, Worker callbacks, cancellation, retry, recovery, controllers,
Spring service wiring, or frontend integration.

## Migration Contract

Migration scripts are placed under `script/sql/migrations/` and are ordered:

1. `V20260819_01__create_multisource_core_tables.sql`
2. `V20260819_02__create_multisource_indexes.sql`
3. `V20260819_03__seed_multisource_profiles.sql`

Each file begins with purpose, dependency, and compensating-rollback comments.
Scripts contain no `public.` qualification, so production execution uses the
normal deployment `search_path` and integration tests can redirect all objects
to a disposable schema.

`rs_schema_migration` is created by the first script and records version,
filename, SHA-256, and applied time. The deployment runner specified by the
architecture remains a later infrastructure concern; this slice provides the
table and immutable scripts it will validate.

## Schema

The first migration creates these `rs_` tables:

| Table | Responsibility |
| --- | --- |
| `rs_schema_migration` | applied migration audit |
| `rs_processing_batch` | explicit parent batch |
| `rs_batch_product` | immutable batch candidate and input-asset snapshots |
| `rs_processing_task` | task lifecycle, plan and request snapshots |
| `rs_task_input_asset` | immutable input asset snapshots, associated with a candidate package |
| `rs_task_product` | selected candidate/profile snapshots |
| `rs_task_stage` | materialized plan stages and future lease fields |
| `rs_task_stage_dependency` | DAG dependencies for Slice B |
| `rs_task_event` | Worker event and stale-event audit for Slice B |
| `rs_artifact` | intermediate and final artifact registry |
| `rs_quality_result` | quality findings independent of lifecycle status |
| `rs_product_profile` | published product profiles |
| `rs_processing_capability` | published processing capabilities |
| `rs_profile_capability` | profile/capability publication relation |

Foreign keys exist only within `rs_` tables. `creator_id` remains a scalar:
there is no foreign key to `sys_user`. Input assets are likewise snapshots and
have no hard reference to legacy file tables.

`rs_task_stage` includes `execution_id`, `worker_id`, `lease_expire_at`,
`heartbeat_at`, error and log fields even though Slice A only writes a
`PENDING` stage. `rs_task_event` has the unique key
`(stage_id, attempt, event_sequence)` and an `acceptance_status` field so
Slice B can record stale callbacks without changing task state.

The index script adds the architecture-required lifecycle, batch, stage lease,
artifact, and profile lookup indexes. The seed script inserts only four
`SUPPORTED` v1 profiles, matching the approved first sample set:
`FY-3H-MERSI`, `HY-1C-CZI`, `FY-4B-AGRI`, and `HY-2D-ALTIMETER`. It does not
seed unverified CFOSAT, GF, SAR, or SPOT products.

## Persistence Mapping

MyBatis-Plus entities live exclusively under
`com.htht.multisource.infrastructure.persistence.entity`; mappers and
repository adapters live in sibling packages. The module adds direct
MyBatis-Plus and PostgreSQL JDBC dependencies rather than importing
`htht-system` or `htht-algorithm`.

Repository adapters map domain records explicitly:

- a `ProcessingTask` maps to its task row and one `rs_task_product` row for
  each selected candidate;
- each candidate file maps to `rs_task_input_asset` with its owning
  `package_id` and `(task_id, package_id, role, ordinal)` uniqueness,
  including its `storageUriSnapshot`; this keeps inputs distinct when multiple
  candidates use the same role and ordinal;
- a `TaskStage` maps to `rs_task_stage` and always retains its task ID,
  stage key, status, and attempt. Its stage type is resolved from the owning
  task's versioned `plan_snapshot`, avoiding a second mutable representation;
- an `Artifact` maps to `rs_artifact` with URI and checksum preserved;
- a `ProcessingBatch` maps to `rs_processing_batch` before child tasks.
  Its selected candidates are preserved in `rs_batch_product`, including each
  candidate's JSONB input-asset snapshot, so a batch remains readable before
  it has child tasks.

JSON snapshots are stored as JSONB. `ProcessingTask.parameterSnapshot` is
validated as a nonblank JSON object before insertion. The plan snapshot is a
canonical JSON representation generated by the persistence adapter from its
versioned stages and dependencies. It never invokes a Worker or legacy business
service.

## Database Verification

`PostgreSqlMigrationIntegrationTest` reads the development JDBC settings from
test-only environment variables, not source-controlled credentials. The test
requires `MULTISOURCE_IT_JDBC_URL`, `MULTISOURCE_IT_USERNAME`, and
`MULTISOURCE_IT_PASSWORD`; it is skipped only when all three are absent.

When configured, each test:

1. creates a schema named `rs_ms_it_<random lowercase UUID>`;
2. opens a new connection with `SET search_path TO <quoted schema>`;
3. executes the three migration resources in order;
4. verifies table, constraint, index, seed, and repository round-trip facts;
5. drops that exact schema with `DROP SCHEMA ... CASCADE` in `finally`;
6. opens a fresh connection and fails unless the schema no longer exists.

The test never sets `search_path` to `public`, never truncates or drops an
existing schema, and does not create data in legacy tables. PostgreSQL server
connection logs may still record the test connection, which is outside business
data state.

## Out of Scope

This design intentionally does not implement the migration deployment runner,
advisory-lock acquisition, Worker `execute-stage` transport, event acceptance,
or execution orchestration. Those actions require a separate Slice B design
after the durable schema and repository adapters have passed integration
verification.
