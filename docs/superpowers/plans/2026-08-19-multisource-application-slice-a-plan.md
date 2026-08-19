# Multisource Application Slice A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a pure-Java, testable application slice for multi-candidate
preflight, batch and task creation, and owner-scoped task detail queries.

**Architecture:** Application services depend only on domain objects and
outbound ports. Worker inspection data is translated into
`InspectionResponse` and `PreflightResult`; task creation accepts only a
selected `SUPPORTED` candidate, persists a batch/task/initial stages through
ports, and never dispatches a Worker process. Execution dispatch, callbacks,
retry, cancellation, and recovery remain deferred until durable stage-event
storage and the `execute-stage` contract are implemented.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Maven, existing
`htht-multisource` module.

**Design:** `docs/superpowers/specs/2026-08-19-application-slice-boundary-design.md`

---

## File Structure

- `application/port/out/model/InspectionResponse.java`: immutable Worker inspection
  response containing rule package version and all candidates.
- `application/port/out/ProcessingBatchRepository.java`: persists a batch
  separately from child tasks.
- `application/port/out/*IdGenerator.java`: allocates task, batch, and stage
  identifiers without coupling the application layer to database sequences.
- `application/usecase/model/PreflightResult.java`: application-facing
  candidate list returned to callers.
- `application/usecase/model/BatchCreateCommand.java`: immutable input for
  one batch and its selected candidates.
- `application/usecase/model/BatchCreationResult.java`: persisted batch plus
  child task identifiers.
- `application/usecase/model/TaskDetailView.java`,
  `TaskStageView.java`, `TaskArtifactView.java`: immutable read model.
- `application/usecase/service/TaskCreationCoordinator.java`: shared
  plan-and-persist sequence used by direct and batch creation.
- `application/usecase/service/*Service.java`: one service per use case.
- `src/test/java/.../ApplicationUseCaseTestFixtures.java`: reusable,
  package-local domain fixtures for the three application-use-case test classes.

### Task 1: Normalize the Inspection and Read-Grant Contracts

**Files:**
- Create: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ApplicationUseCaseTestFixtures.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/InspectionResponse.java`
- Delete: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/InspectionResult.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ProductInspector.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/AssetStorage.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/PlanRequest.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/PreflightProductUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/PreflightResult.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/PreflightProductService.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/PreflightProductUseCaseTest.java`

- [x] **Step 1: Write the preflight test before changing production contracts**

Create this reusable package-local fixture class first; every test in this plan
uses static imports for `candidate(...)`, `samplePlan()`, `subject()`, and
`task(...)`.

```java
final class ApplicationUseCaseTestFixtures {
    private ApplicationUseCaseTestFixtures() {
    }

    static AccessSubject subject() {
        return new AccessSubject(7L);
    }

    static ProductCandidate candidate(String packageId, ProductSupportStatus supportStatus) {
        ProductProfileKey key = new ProductProfileKey("FY-3H-MERSI", "v1");
        InputAsset asset = new InputAsset(
            "MAIN", 0, new AssetReference("LOCAL_FILE", packageId + ".hdf"),
            "sha256:" + packageId, true
        );
        return new ProductCandidate(
            packageId, key, ProductDataShape.SWATH_RASTER, supportStatus, List.of(asset), 0.91
        );
    }

    static ProcessingPlan samplePlan() {
        return new ProcessingPlan("plan-v1", List.of(
            new ProcessingStage("READ", "READ_SOURCE", List.of()),
            new ProcessingStage("CAL", "CALIBRATE", List.of("READ"))
        ));
    }

    static ProcessingTask task(TaskId taskId, AccessSubject creator) {
        ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        ProcessingBatch batch = new ProcessingBatch(
            11L, "batch-01", ProcessingBatchStatus.CREATED, List.of(candidate)
        );
        return new ProcessingTask(
            taskId, creator, TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED,
            samplePlan(), batch, List.of(candidate)
        );
    }
}
```

```java
@Test
void preflightReturnsEveryCandidateFromInspector() {
    ProductCandidate first = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
    ProductCandidate second = candidate("fy3h-granule-02", ProductSupportStatus.CANDIDATE);
    PreflightProductService service = new PreflightProductService(
        request -> new InspectionResponse("rules-2026.08", List.of(first, second)),
        new FakeAssetStorage()
    );

    PreflightResult result = service.preflight(subject(), List.of(first.files().get(0)));

    assertThat(result.rulePackageVersion()).isEqualTo("rules-2026.08");
    assertThat(result.candidates()).containsExactly(first, second);
    assertThatThrownBy(() -> result.candidates().add(first))
        .isInstanceOf(UnsupportedOperationException.class);
}
```

Use this test-local `AssetStorage` fake:

```java
private static final class FakeAssetStorage implements AssetStorage {
    @Override
    public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
        return new AssetAccessGrant("read-1", Instant.parse("2026-08-20T00:00:00Z"));
    }

    @Override
    public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
        throw new UnsupportedOperationException("Execution grants are outside preflight tests.");
    }

    @Override
    public AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
        throw new UnsupportedOperationException("Asset resolution is outside preflight tests.");
    }
}
```

- [x] **Step 2: Run the focused test and confirm it fails because the types are absent**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -s 'C:\Users\24257\Desktop\GIS开发\.tools\maven-worktree-settings.xml' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=PreflightProductUseCaseTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `InspectionResponse`, `PreflightResult`, or
`PreflightProductService` does not exist.

- [x] **Step 3: Implement the minimal inspection boundary**

```java
public record InspectionResponse(String rulePackageVersion, List<ProductCandidate> candidates) {
    public InspectionResponse {
        if (rulePackageVersion == null || rulePackageVersion.isBlank()) {
            throw new IllegalArgumentException("Rule package version must not be blank.");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Inspection candidates must not be empty.");
        }
    }
}

public interface ProductInspector {
    InspectionResponse inspect(InspectionRequest request);
}

public interface AssetStorage {
    AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs);
    ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri);
    AssetMetadata resolve(AccessSubject subject, AssetReference reference);
}
```

Use these matching application types:

```java
public record PreflightResult(String rulePackageVersion, List<ProductCandidate> candidates) {
    public PreflightResult {
        if (rulePackageVersion == null || rulePackageVersion.isBlank()) {
            throw new IllegalArgumentException("Rule package version must not be blank.");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }
}

public interface PreflightProductUseCase {
    PreflightResult preflight(AccessSubject subject, List<InputAsset> inputs);
}

public final class PreflightProductService implements PreflightProductUseCase {
    private final ProductInspector inspector;
    private final AssetStorage assetStorage;

    public PreflightProductService(ProductInspector inspector, AssetStorage assetStorage) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
        this.assetStorage = Objects.requireNonNull(assetStorage, "assetStorage");
    }

    @Override
    public PreflightResult preflight(AccessSubject subject, List<InputAsset> inputs) {
        List<InputAsset> copiedInputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        InspectionResponse response = inspector.inspect(
            new InspectionRequest(subject, copiedInputs, assetStorage.grantRead(subject, copiedInputs))
        );
        return new PreflightResult(response.rulePackageVersion(), response.candidates());
    }
}
```

Change `PlanRequest` to:

```java
public record PlanRequest(
    TaskId taskId,
    ProductCandidate candidate,
    String parameterSnapshot,
    AssetAccessGrant readGrant
) {
    public PlanRequest {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(readGrant, "readGrant");
        if (parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Parameter snapshot must not be blank.");
        }
    }
}
```

- [x] **Step 4: Run the focused test and confirm it passes**

Run the command from Step 2.

Expected: `PreflightProductUseCaseTest` passes with zero failures and zero
errors.

- [x] **Step 5: Commit the normalized inspection contract**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/application
git add htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/PreflightProductUseCaseTest.java
git commit -m "feat: add multisource preflight application service"
```

### Task 2: Add Batch and Identifier Output Ports

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ProcessingBatchRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskIdGenerator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ProcessingBatchIdGenerator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/StageIdGenerator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/BatchCreateCommand.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/BatchCreationResult.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/CreateProcessingTaskCommand.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCaseTest.java`

- [x] **Step 1: Write tests for immutable commands and allocated identifiers**

```java
@Test
void batchCommandCopiesSelectedCandidates() {
    ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
    List<ProductCandidate> selected = new ArrayList<>(List.of(candidate));

    BatchCreateCommand command = new BatchCreateCommand(
        "batch-01", subject(), selected, "{\"mode\":\"standard\"}", new StorageUri("rs://output/batch-01")
    );
    selected.clear();

    assertThat(command.candidates()).containsExactly(candidate);
    assertThatThrownBy(() -> command.candidates().add(candidate))
        .isInstanceOf(UnsupportedOperationException.class);
}
```

- [x] **Step 2: Run the focused test and confirm it fails because the command is absent**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -s 'C:\Users\24257\Desktop\GIS开发\.tools\maven-worktree-settings.xml' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=CreateProcessingTaskUseCaseTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `BatchCreateCommand` does not exist.

- [x] **Step 3: Implement the ports and commands**

```java
public interface ProcessingBatchRepository {
    Optional<ProcessingBatch> findById(long batchId);
    void save(ProcessingBatch batch);
}

public interface TaskIdGenerator {
    TaskId nextId();
}

public interface ProcessingBatchIdGenerator {
    long nextId();
}

public interface StageIdGenerator {
    StageId nextId();
}
```

```java
public record BatchCreateCommand(
    String name,
    AccessSubject subject,
    List<ProductCandidate> candidates,
    String parameterSnapshot,
    StorageUri outputUri
) {
    public BatchCreateCommand {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(outputUri, "outputUri");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (name == null || name.isBlank() || candidates.isEmpty()
            || parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Batch create command is incomplete.");
        }
    }
}

public record BatchCreationResult(ProcessingBatch batch, List<TaskId> taskIds) {
    public BatchCreationResult {
        Objects.requireNonNull(batch, "batch");
        taskIds = List.copyOf(Objects.requireNonNull(taskIds, "taskIds"));
        if (taskIds.isEmpty()) {
            throw new IllegalArgumentException("Batch must create at least one task.");
        }
    }
}
```

Replace `CreateProcessingTaskCommand` with:

```java
public record CreateProcessingTaskCommand(
    String name,
    AccessSubject subject,
    ProductCandidate candidate,
    String parameterSnapshot,
    StorageUri outputUri
) {
    public CreateProcessingTaskCommand {
        if (name == null || name.isBlank() || parameterSnapshot == null || parameterSnapshot.isBlank()) {
            throw new IllegalArgumentException("Task create command is incomplete.");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(outputUri, "outputUri");
    }
}
```

- [x] **Step 4: Run the focused test and confirm it passes**

Run the command from Step 2.

Expected: `CreateProcessingTaskUseCaseTest` passes with zero failures and zero
errors.

- [x] **Step 5: Commit the creation ports**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/application
git add htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCaseTest.java
git commit -m "feat: add multisource batch creation ports"
```

### Task 3: Implement Direct and Batch Task Creation

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/CreateProcessingBatchUseCase.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/CreateProcessingTaskService.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/CreateProcessingBatchService.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCaseTest.java`

- [ ] **Step 1: Write failing tests for supported and unsafe candidates**

```java
@Test
void batchCreationPersistsOneChildTaskAndPendingStagesPerSupportedCandidate() {
    ProductCandidate first = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
    ProductCandidate second = candidate("hy1c-granule-01", ProductSupportStatus.SUPPORTED);
    Fakes fakes = new Fakes(samplePlan());
    CreateProcessingBatchService service = fakes.batchService();

    BatchCreationResult result = service.create(new BatchCreateCommand(
        "batch-01", subject(), List.of(first, second), "{\"mode\":\"standard\"}", new StorageUri("rs://output/batch-01")
    ));

    assertThat(result.batch().candidates()).containsExactly(first, second);
    assertThat(result.taskIds()).containsExactly(TaskId.of(101), TaskId.of(102));
    assertThat(fakes.tasks.saved()).hasSize(2);
    assertThat(fakes.stages.saved()).hasSize(4);
}

@Test
void unsafeCandidateIsRejectedBeforePlannerIsCalled() {
    Fakes fakes = new Fakes(samplePlan());

    assertThatThrownBy(() -> fakes.taskService().create(new CreateProcessingTaskCommand(
        "unknown-01", subject(), candidate("unknown-01", ProductSupportStatus.UNKNOWN_PRODUCT),
        "{\"mode\":\"standard\"}", new StorageUri("rs://output/unknown-01")
    ))).isInstanceOf(IllegalArgumentException.class);

    assertThat(fakes.planner.requests()).isEmpty();
}
```

Use this test-local fixture set. It supplies only application output ports and
does not use Spring, SQL, or a Worker process:

```java
private static final class Fakes {
    private final InMemoryBatchRepository batches = new InMemoryBatchRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final InMemoryStageRepository stages = new InMemoryStageRepository();
    private final FixedPlanner planner;
    private final TaskCreationCoordinator coordinator;

    private Fakes(ProcessingPlan plan) {
        planner = new FixedPlanner(plan);
        coordinator = new TaskCreationCoordinator(
            tasks, stages, planner, new CreationAssetStorage(),
            new IncrementingTaskIdGenerator(), new IncrementingStageIdGenerator()
        );
    }

    private CreateProcessingTaskService taskService() {
        return new CreateProcessingTaskService(
            batches, new IncrementingBatchIdGenerator(), coordinator
        );
    }

    private CreateProcessingBatchService batchService() {
        return new CreateProcessingBatchService(
            batches, new IncrementingBatchIdGenerator(), coordinator
        );
    }
}

private static final class InMemoryBatchRepository implements ProcessingBatchRepository {
    private final List<ProcessingBatch> saved = new ArrayList<>();

    @Override
    public Optional<ProcessingBatch> findById(long batchId) {
        return saved.stream().filter(batch -> batch.id() == batchId).findFirst();
    }

    @Override
    public void save(ProcessingBatch batch) {
        saved.add(batch);
    }
}

private static final class InMemoryTaskRepository implements ProcessingTaskRepository {
    private final List<ProcessingTask> saved = new ArrayList<>();

    @Override
    public Optional<ProcessingTask> findById(TaskId taskId) {
        return saved.stream().filter(task -> task.id().equals(taskId)).findFirst();
    }

    @Override
    public void save(ProcessingTask task) {
        saved.add(task);
    }

    private List<ProcessingTask> saved() {
        return List.copyOf(saved);
    }
}

private static final class InMemoryStageRepository implements TaskStageRepository {
    private final List<TaskStage> saved = new ArrayList<>();

    @Override
    public Optional<TaskStage> findById(StageId stageId) {
        return saved.stream().filter(stage -> stage.id().equals(stageId)).findFirst();
    }

    @Override
    public List<TaskStage> findByTaskId(TaskId taskId) {
        return saved.stream().filter(stage -> stage.taskId().equals(taskId)).toList();
    }

    @Override
    public void save(TaskStage stage) {
        saved.add(stage);
    }

    private List<TaskStage> saved() {
        return List.copyOf(saved);
    }
}

private static final class FixedPlanner implements ProcessingPlanner {
    private final ProcessingPlan plan;
    private final List<PlanRequest> requests = new ArrayList<>();

    private FixedPlanner(ProcessingPlan plan) {
        this.plan = plan;
    }

    @Override
    public ProcessingPlan buildPlan(PlanRequest request) {
        requests.add(request);
        return plan;
    }

    private List<PlanRequest> requests() {
        return List.copyOf(requests);
    }
}

private static final class CreationAssetStorage implements AssetStorage {
    @Override
    public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
        return new AssetAccessGrant("read-" + subject.userId(), Instant.parse("2026-08-20T00:00:00Z"));
    }

    @Override
    public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
        throw new UnsupportedOperationException("Execution grants are outside creation tests.");
    }

    @Override
    public AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
        throw new UnsupportedOperationException("Asset resolution is outside creation tests.");
    }
}

private static final class IncrementingTaskIdGenerator implements TaskIdGenerator {
    private long next = 101L;

    @Override
    public TaskId nextId() {
        return TaskId.of(next++);
    }
}

private static final class IncrementingBatchIdGenerator implements ProcessingBatchIdGenerator {
    private long next = 11L;

    @Override
    public long nextId() {
        return next++;
    }
}

private static final class IncrementingStageIdGenerator implements StageIdGenerator {
    private long next = 201L;

    @Override
    public StageId nextId() {
        return new StageId(next++);
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails because services are absent**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -s 'C:\Users\24257\Desktop\GIS开发\.tools\maven-worktree-settings.xml' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=CreateProcessingTaskUseCaseTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `CreateProcessingBatchService` and
`CreateProcessingTaskService` do not exist.

- [ ] **Step 3: Implement the shared creation sequence**

```java
public interface CreateProcessingTaskUseCase {
    TaskId create(CreateProcessingTaskCommand command);
}

public interface CreateProcessingBatchUseCase {
    BatchCreationResult create(BatchCreateCommand command);
}
```

`TaskCreationCoordinator` must implement this exact sequence:

```java
TaskId create(ProcessingBatch batch, AccessSubject subject, ProductCandidate candidate,
              String parameterSnapshot, StorageUri outputUri) {
    if (candidate.supportStatus() != ProductSupportStatus.SUPPORTED) {
        throw new IllegalArgumentException("Only supported candidates may create processing tasks.");
    }
    TaskId taskId = taskIdGenerator.nextId();
    AssetAccessGrant readGrant = assetStorage.grantRead(subject, candidate.files());
    ProcessingPlan plan = planner.buildPlan(new PlanRequest(taskId, candidate, parameterSnapshot, readGrant));
    ProcessingTask task = new ProcessingTask(
        taskId, subject, TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED, plan, batch, List.of(candidate)
    );
    taskRepository.save(task);
    for (ProcessingStage stage : plan.stages()) {
        taskStageRepository.save(new TaskStage(
            stageIdGenerator.nextId(), taskId, stage.key(), StageExecutionStatus.PENDING, 1
        ));
    }
    return taskId;
}
```

`CreateProcessingTaskService.create(...)` creates and persists a
single-candidate `ProcessingBatch` with `ProcessingBatchStatus.CREATED`, then
delegates to the coordinator. `CreateProcessingBatchService.create(...)`
validates every selected candidate before persisting anything, creates one
batch, calls the coordinator once per candidate, and returns
`new BatchCreationResult(batch, taskIds)`.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run the command from Step 2.

Expected: `CreateProcessingTaskUseCaseTest` passes with zero failures and zero
errors. It proves that unsafe candidates never reach `ProcessingPlanner`.

- [ ] **Step 5: Commit the creation services**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/application
git add htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCaseTest.java
git commit -m "feat: add multisource task creation services"
```

### Task 4: Implement Owner-Scoped Task Detail

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/GetTaskDetailUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskDetailView.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskStageView.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskArtifactView.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/GetTaskDetailService.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/GetTaskDetailUseCaseTest.java`

- [ ] **Step 1: Write failing owner and non-owner detail tests**

```java
@Test
void ownerCanReadTaskStagesAndArtifacts() {
    ProcessingTask task = task(TaskId.of(101), new AccessSubject(7L));
    GetTaskDetailService service = new GetTaskDetailService(
        new SingleTaskRepository(task),
        new SingleStageRepository(TaskId.of(101)),
        new SingleArtifactRepository(TaskId.of(101))
    );

    TaskDetailView detail = service.get(TaskId.of(101), new AccessSubject(7L));

    assertThat(detail.id()).isEqualTo(TaskId.of(101));
    assertThat(detail.stages()).extracting(TaskStageView::stageKey).containsExactly("READ");
    assertThat(detail.artifacts()).extracting(TaskArtifactView::type).containsExactly("MANIFEST");
}

@Test
void nonOwnerCannotReadTaskDetail() {
    ProcessingTask task = task(TaskId.of(101), new AccessSubject(7L));
    GetTaskDetailService service = new GetTaskDetailService(
        new SingleTaskRepository(task),
        new EmptyTaskStageRepository(),
        new EmptyArtifactRepository()
    );

    assertThatThrownBy(() -> service.get(TaskId.of(101), new AccessSubject(8L)))
        .isInstanceOf(SecurityException.class);
}
```

Use these test-local repository fakes:

```java
private static final class SingleTaskRepository implements ProcessingTaskRepository {
    private final ProcessingTask task;

    private SingleTaskRepository(ProcessingTask task) {
        this.task = task;
    }

    @Override
    public Optional<ProcessingTask> findById(TaskId taskId) {
        return task.id().equals(taskId) ? Optional.of(task) : Optional.empty();
    }

    @Override
    public void save(ProcessingTask task) {
        throw new UnsupportedOperationException("Detail tests are read-only.");
    }
}

private static final class SingleStageRepository implements TaskStageRepository {
    private final TaskId taskId;

    private SingleStageRepository(TaskId taskId) {
        this.taskId = taskId;
    }

    @Override
    public Optional<TaskStage> findById(StageId stageId) {
        return Optional.empty();
    }

    @Override
    public List<TaskStage> findByTaskId(TaskId taskId) {
        return this.taskId.equals(taskId)
            ? List.of(new TaskStage(new StageId(201), taskId, "READ", StageExecutionStatus.PENDING, 1))
            : List.of();
    }

    @Override
    public void save(TaskStage stage) {
        throw new UnsupportedOperationException("Detail tests are read-only.");
    }
}

private static final class SingleArtifactRepository implements ArtifactRepository {
    private final TaskId taskId;

    private SingleArtifactRepository(TaskId taskId) {
        this.taskId = taskId;
    }

    @Override
    public List<Artifact> findByTaskId(TaskId taskId) {
        return this.taskId.equals(taskId)
            ? List.of(new Artifact(taskId, new StageId(201), "MANIFEST",
                new StorageUri("rs://output/101/manifest.json"), "sha256:abc"))
            : List.of();
    }

    @Override
    public void save(Artifact artifact) {
        throw new UnsupportedOperationException("Detail tests are read-only.");
    }
}

private static final class EmptyTaskStageRepository implements TaskStageRepository {
    @Override
    public Optional<TaskStage> findById(StageId stageId) {
        return Optional.empty();
    }

    @Override
    public List<TaskStage> findByTaskId(TaskId taskId) {
        return List.of();
    }

    @Override
    public void save(TaskStage stage) {
        throw new UnsupportedOperationException("Detail tests are read-only.");
    }
}

private static final class EmptyArtifactRepository implements ArtifactRepository {
    @Override
    public List<Artifact> findByTaskId(TaskId taskId) {
        return List.of();
    }

    @Override
    public void save(Artifact artifact) {
        throw new UnsupportedOperationException("Detail tests are read-only.");
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails because the detail service is absent**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -s 'C:\Users\24257\Desktop\GIS开发\.tools\maven-worktree-settings.xml' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=GetTaskDetailUseCaseTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `GetTaskDetailService` and the detail view
types do not exist.

- [ ] **Step 3: Implement immutable detail views and service**

```java
public interface GetTaskDetailUseCase {
    TaskDetailView get(TaskId taskId, AccessSubject subject);
}

public record TaskStageView(StageId id, String stageKey, StageExecutionStatus status, int attempt) {}

public record TaskArtifactView(StageId stageId, String type, StorageUri uri, String checksum) {}

public record TaskDetailView(
    TaskId id,
    long batchId,
    TaskLifecycleStatus lifecycleStatus,
    QualityStatus qualityStatus,
    ProcessingPlan plan,
    List<ProductCandidate> candidates,
    List<TaskStageView> stages,
    List<TaskArtifactView> artifacts
) {
    public TaskDetailView {
        candidates = List.copyOf(candidates);
        stages = List.copyOf(stages);
        artifacts = List.copyOf(artifacts);
    }
}
```

`GetTaskDetailService.get(...)` must:

1. Load the task with `ProcessingTaskRepository.findById(taskId)`.
2. Throw `NoSuchElementException("Processing task not found.")` when absent.
3. Throw `SecurityException("Processing task access denied.")` when
   `task.creator()` does not equal `subject`.
4. Map `TaskStageRepository.findByTaskId(taskId)` and
   `ArtifactRepository.findByTaskId(taskId)` into the views above.
5. Return `TaskDetailView` without changing task, stage, or artifact state.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run the command from Step 2.

Expected: `GetTaskDetailUseCaseTest` passes with zero failures and zero errors.

- [ ] **Step 5: Run all Slice A tests and commit**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  -s 'C:\Users\24257\Desktop\GIS开发\.tools\maven-worktree-settings.xml' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=PreflightProductUseCaseTest,CreateProcessingTaskUseCaseTest,GetTaskDetailUseCaseTest' `
  -DfailIfNoTests=false clean test
```

Expected: all three test classes pass with zero failures and zero errors.

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/application
git add htht-modules/htht-multisource/src/test/java/com/htht/multisource/application
git commit -m "feat: add multisource task detail service"
```

## Deferred Execution Slice

Do not implement `DispatchNextStageUseCase`, `ReceiveWorkerEventUseCase`,
`RecoverExpiredStageUseCase`, cancellation dispatch, or retry dispatch in this
plan. Their implementation begins only after the migration and repository work
persists stage dependencies, execution leases, event sequences, and acceptance
status, and after the Worker Gateway defines the versioned `execute-stage`
request and response contract.
