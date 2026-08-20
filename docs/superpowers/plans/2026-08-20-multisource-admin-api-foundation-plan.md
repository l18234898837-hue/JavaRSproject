# Multisource Admin API Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the existing multisource Slice A use cases through real authenticated Admin HTTP APIs backed by `sys_file` asset resolution and Spring bean assembly.

**Architecture:** Keep domain and application code in `htht-multisource`. Add a `PreflightSessionStore` port and its in-memory implementation in `htht-multisource`; add the HTTP DTO/controller, current-user adapter, `SysFileAssetStorageAdapter`, and Spring composition in `htht-admin`. The controller only validates and maps requests; it never accesses mappers, constructs paths, starts Workers, or implements business rules. This plan deliberately excludes task execution orchestration, cancellation, retries, progress events, artifacts, quality reports, and frontend changes.

**Tech Stack:** Java 17, Spring Boot 2.7, Spring MVC, JUnit 5, MockMvc, Mockito, MyBatis-Plus, PostgreSQL/PostGIS.

**Implementation status (2026-08-20):** Task 1 is in progress. `AssetRequest` is implemented as an immutable `SYS_FILE`-only reference and covered by two executed `dev`-tagged unit tests. Task 2 (expiring preflight sessions) is implemented in `htht-multisource` and covered by three executed `dev`-tagged unit tests. The remaining request/response DTOs, the asset adapter, controllers, and Spring assembly have not yet been implemented.

---

## File Map

- Create `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`: Spring beans for asset storage, Worker gateway, repositories, ID generators, planner, and Slice A services.
- Create `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/PreflightSessionStore.java`: application port for owned, expiring candidate snapshots.
- Create `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/session/InMemoryPreflightSessionStore.java`: initial atomic in-memory implementation with a 30-minute TTL.
- Create `htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java`: resolve `SYS_FILE` IDs through `ISysFileService`, map them to immutable `InputAsset` snapshots with `rs://sys-file/{id}` URIs, issue grants, and resolve those URIs for the Worker manifest.
- Create `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingTaskController.java`: preflight, single-task creation, and owner-scoped task detail endpoints.
- Create `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingBatchController.java`: batch creation endpoint.
- Create DTOs under `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/`: request bodies and response views only.
- Create tests under `htht-admin/src/test/java/com/htht/web/controller/multisource/` and `htht-admin/src/test/java/com/htht/config/multisource/`.
- Modify `htht-admin/pom.xml` only if the existing module does not already depend on `htht-multisource`.

## Task 1: Freeze HTTP Contract And DTO Validation

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/AssetRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/PreflightRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/CreateTaskRequest.java` accepting `preflightToken` and a server-issued `candidateId` rather than a candidate object.
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/CreateBatchRequest.java` accepting `preflightToken` and server-issued candidate IDs rather than candidate objects.
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskIdResponse.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/BatchCreationResponse.java`
- Create: `htht-admin/src/test/java/com/htht/web/controller/multisource/MultisourceDtoValidationTest.java`

- [ ] **Step 1: Write failing validation tests**

Test that a request rejects a blank name, an empty asset list, a non-positive `sourceId`, a source type other than `SYS_FILE`, a blank or missing preflight token, blank parameter JSON, and an empty candidate-ID list for batch creation. Test that input order and `ordinal` survive mapping.

- [ ] **Step 2: Run the focused tests and confirm the DTO contract is absent**

Run `mvn -pl htht-admin -am -Dtest=MultisourceDtoValidationTest test`.
Expected: compilation or assertion failure because the DTOs and validation rules do not exist.

- [ ] **Step 3: Implement immutable request DTOs and explicit mapping data**

Use records or the repository's established validated request style. The minimum asset shape is:

```java
public record AssetRequest(
    @NotBlank String sourceType,
    @Positive long sourceId,
    @NotBlank String role,
    @PositiveOrZero int ordinal,
    boolean required
) {}
```

Validate `sourceType.equals("SYS_FILE")` at the adapter boundary as well; HTTP validation alone is not a security boundary.

- [ ] **Step 4: Run the focused tests and commit**

Run the same Maven command; expect all DTO tests to pass. Commit with `feat: define multisource admin api dto contract`.

## Task 2: Implement Expiring Preflight Sessions

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/PreflightSessionStore.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/session/InMemoryPreflightSessionStore.java`
- Create: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/session/InMemoryPreflightSessionStoreTest.java`

- [x] **Step 1: Write failing session-store tests**

Prove that a stored token is opaque, bound to its owner, expires after 30 minutes, returns only server-captured candidate snapshots, and is atomically consumed only after successful completion. Rejected creation must not consume it.

- [x] **Step 2: Run focused tests and confirm the store is absent**

Run `mvn -pl htht-modules\\htht-multisource -am -Dtest=InMemoryPreflightSessionStoreTest test`.

- [x] **Step 3: Implement port and in-memory store**

Use `Clock` and a secure random token generator through constructor injection. Do not accept or reconstruct candidates from client data. Keep the store internal to the single Admin process and document that service restart invalidates outstanding tokens.

- [x] **Step 4: Run focused tests and commit**

Run the focused test and `git diff --check`; expect all tests to pass. Commit with `feat: preserve server-side preflight candidates`.

## Task 3: Implement The `sys_file` Asset Adapter

**Files:**
- Create: `htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java`
- Create: `htht-admin/src/test/java/com/htht/config/multisource/SysFileAssetStorageAdapterTest.java`

- [ ] **Step 1: Write failing adapter tests**

Mock `ISysFileService` with a temporary regular file below a configured upload root. Verify the adapter maps `SYS_FILE` requests to immutable `InputAsset` values with `rs://sys-file/{id}` and a stable SHA-256 checksum; verify `grantRead` returns a non-expired grant; verify the Worker resolver maps that URI back to the same server-side file. Unknown IDs, directories, paths outside the upload root, and `sourceType != SYS_FILE` must fail. Verify the adapter never reads a client-provided path.

- [ ] **Step 2: Run the adapter tests and confirm the adapter is absent**

Run `mvn -pl htht-admin -am -Dtest=SysFileAssetStorageAdapterTest test`.
Expected: compilation failure for the missing adapter.

- [ ] **Step 3: Implement server-side resolution and snapshotting**

Load `SysFile` by ID through `ISysFileService.getById`. Resolve only its server-side `filePath`, require that its real path is a readable regular file below `${file.upload-path}`, compute SHA-256 by streaming bytes, and construct `AssetReference`, `StorageUri("rs://sys-file/{id}")`, `InputAsset`, and `AssetAccessGrant` values. Implement `LocalWorkerAssetResolver` by parsing only that URI pattern and reapplying the same root check. Do not add ownership claims that `sys_file` cannot prove; document the limitation in the class Javadoc.

- [ ] **Step 4: Run the adapter tests and commit**

Run the focused test and `git diff --check`; expect all tests to pass. Commit with `feat: adapt sys file assets for multisource api`.

## Task 4: Add Controllers For Slice A

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingTaskController.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingBatchController.java`
- Create: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingTaskControllerTest.java`
- Create: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingBatchControllerTest.java`

- [ ] **Step 1: Write failing MockMvc tests**

Mock the four use cases, current-user provider, and preflight-session service. Verify:

```text
POST /api/multisource/processing-tasks/preflight -> 200 and preflightToken plus candidates[]
POST /api/multisource/processing-tasks -> 201 and taskId after an owned, unexpired candidate selection
POST /api/multisource/processing-batches -> 201 and batchId plus taskIds[] after owned, unexpired candidate selections
GET  /api/multisource/processing-tasks/{id} -> 200 and lifecycleStatus, stages[], artifacts[]
```

Also verify malformed JSON, blank names, unsupported source types, and an absent task produce the repository's normal client/error response without invoking a Worker directly.

- [ ] **Step 2: Run the controller tests and confirm missing web components**

Run `mvn -pl htht-admin -am -Dtest=ProcessingTaskControllerTest,ProcessingBatchControllerTest test`.
Expected: compilation failure or missing-route failure because the controllers and DTO mappings are absent.

- [ ] **Step 3: Implement thin controllers and response mapping**

Use the project's standard response envelope and `LoginHelper.getUserId()`. Map incoming `SYS_FILE` IDs through `SysFileAssetStorageAdapter`, create `AccessSubject` from the authenticated user, then preflight and store server-side candidates before returning a token. Task and batch creation load candidates only from the owned unexpired session and consume it after successful creation. Delegate to `PreflightProductUseCase`, `CreateProcessingTaskUseCase`, `CreateProcessingBatchUseCase`, and `GetTaskDetailUseCase`; keep routes under `/api/multisource` and return no filesystem path or Worker command.

- [ ] **Step 4: Run controller tests and commit**

Run the two focused test classes and `git diff --check`; expect all tests to pass. Commit with `feat: expose multisource slice a api`.

## Task 5: Assemble Production Spring Beans

**Files:**
- Create: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Create: `htht-admin/src/test/java/com/htht/config/multisource/MultiSourceAdapterConfigurationTest.java`
- Modify: `htht-admin/pom.xml` only if dependency verification requires it.

- [ ] **Step 1: Write the context wiring test**

Use a narrow Spring context with mocked infrastructure and assert that the `AssetStorage`, `PreflightProductUseCase`, `CreateProcessingTaskUseCase`, `CreateProcessingBatchUseCase`, and `GetTaskDetailUseCase` beans are present. Assert the multisource module remains free of imports from `htht-admin` or `htht-system`.

- [ ] **Step 2: Run it and confirm the bean graph is missing**

Run `mvn -pl htht-admin -am -Dtest=MultiSourceAdapterConfigurationTest test`.
Expected: missing-bean failure before configuration exists.

- [ ] **Step 3: Add explicit configuration**

Construct beans with constructor injection, existing repository implementations, ID generators, Worker gateway, and the `SysFileAssetStorageAdapter`. Use `${file.upload-path}` as the Worker manifest `assetRoot`, and bind Worker command, work root, and timeout from an explicit `multisource.worker` configuration prefix. Do not use component scanning to hide cross-module dependencies. Fail startup with a clear message when Worker settings are absent.

- [ ] **Step 4: Run context and API regression tests and commit**

Run `mvn -pl htht-admin -am -Dtest=MultiSourceAdapterConfigurationTest,ProcessingTaskControllerTest,ProcessingBatchControllerTest test`. Expect all focused tests to pass. Commit with `feat: assemble multisource admin adapters`.

## Task 6: Verify The Real Backend Boundary

**Files:**
- Modify: `docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md`
- Verify: existing Slice A, persistence, Worker, and Python test suites.

- [ ] **Step 1: Run focused Java regression**

Run `mvn -pl htht-modules\\htht-multisource,htht-admin -am test` with the same integration profile and database opt-in rules already used by the worktree. Expected: all implemented Java tests pass; PostgreSQL tests may remain skipped when opt-in variables are absent.

- [ ] **Step 2: Run Python regression**

Run `pytest -q` in `rs-algorithm-worker` with configured sample roots. Expected: all existing adapter tests pass.

- [ ] **Step 3: Verify API security boundary**

Confirm no controller accepts `path`, `command`, `python`, `workdir`, or arbitrary output URI fields; confirm unknown `sys_file` IDs fail before a use case is called; confirm task detail always receives the authenticated `AccessSubject`.

- [ ] **Step 4: Update the total plan and commit verification evidence**

Mark Admin API work complete, record any `sys_file` ownership limitation, and explicitly leave execution orchestration deferred. Commit with `docs: record multisource admin api verification`.

## Deferred Follow-up

Create a separate design and implementation plan for execution orchestration before implementing any of the following: dispatching the next DAG stage, receiving and idempotently accepting Worker events, registering artifacts and quality reports, cancellation, retry, timeout recovery, task list, progress, artifact, quality, and capability APIs.
