# Local Worker Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Provide a testable local file-based Worker gateway that implements inspection, plan construction, and stage dispatch ports without owning task state.

**Architecture:** The worker transport is split into a strict JSON codec, a process launcher that owns an isolated request directory, and a gateway that translates validated transport results to application port values. A response file is authoritative; process output is diagnostic only. The implementation follows `docs/superpowers/specs/2026-08-20-local-worker-file-contract-design.md`.

**Tech Stack:** Java 17, Jackson Databind, JUnit 5, AssertJ, Maven.

---

## File Structure

- `infrastructure/worker/WorkerOperation.java`: allowed protocol operations.
- `infrastructure/worker/WorkerProtocolException.java`: operation/request scoped protocol failure.
- `infrastructure/worker/DecodedWorkerResponse.java`: validated response envelope and payload.
- `infrastructure/worker/WorkerContractCodec.java`: strict request encoding and response decoding.
- `infrastructure/worker/WorkerInvocation.java`: immutable input to a process launch.
- `infrastructure/worker/WorkerInvocationResult.java`: immutable process and response-file result.
- `infrastructure/worker/WorkerProcessLauncher.java`: port for the controlled child-process boundary.
- `infrastructure/worker/LocalWorkerProcessLauncher.java`: filesystem and `ProcessBuilder` implementation.
- `infrastructure/worker/LocalProcessWorkerProperties.java`: validated command, work-root, timeout configuration.
- `infrastructure/worker/WorkerResultTranslator.java`: converts decoded JSON payloads to application port values.
- `infrastructure/worker/LocalProcessWorkerGateway.java`: implements `ProductInspector`, `ProcessingPlanner`, and `TaskDispatcher`.
- `src/test/java/.../WorkerContractCodecTest.java`: envelope and response validation tests.
- `src/test/java/.../LocalWorkerProcessLauncherTest.java`: controlled child-process handoff tests.
- `src/test/java/.../LocalProcessWorkerGatewayTest.java`: port-level translation and failure tests.

### Task 1: Define and Test the Versioned JSON Codec

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerOperation.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerProtocolException.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/DecodedWorkerResponse.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

- [x] **Step 1: Write failing codec tests for a versioned stage request and a matching success response**

```java
@Test
void encodesVersionedStageRequestWithRequiredFields() throws Exception {
    String json = codec.encodeStageExecution("req-01", stageRequest());
    JsonNode root = objectMapper.readTree(json);

    assertThat(root.path("schemaVersion").asText()).isEqualTo("1.0");
    assertThat(root.path("operation").asText()).isEqualTo("execute-stage");
    assertThat(root.path("payload").path("taskId").asLong()).isEqualTo(101L);
    assertThat(root.path("payload").path("executionId").asText()).isEqualTo("exec-01");
}

@Test
void rejectsResponseWhoseOperationDoesNotMatchInvocation() {
    String response = """
        {"schemaVersion":"1.0","operation":"inspect","requestId":"req-01","payload":{}}
        """;

    assertThatThrownBy(() -> codec.decodeResponse("req-01", WorkerOperation.EXECUTE_STAGE, response))
        .isInstanceOf(WorkerProtocolException.class)
        .hasMessageContaining("operation");
}
```

- [x] **Step 2: Run the codec tests and confirm they fail because Worker types are absent**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:/Users/24257/.m2/repository' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=WorkerContractCodecTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `WorkerContractCodec`, `WorkerOperation`, and `WorkerProtocolException` do not exist.

- [x] **Step 3: Implement the minimum strict codec**

```java
public enum WorkerOperation {
    INSPECT("inspect"), BUILD_PLAN("build-plan"), EXECUTE_STAGE("execute-stage");

    private final String wireValue;

    WorkerOperation(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}

public final class WorkerProtocolException extends RuntimeException {
    public WorkerProtocolException(String requestId, WorkerOperation operation, String message) {
        super("Worker protocol failure [" + operation.wireValue() + ", " + requestId + "]: " + message);
    }
}

public record DecodedWorkerResponse(String requestId, WorkerOperation operation, JsonNode payload) {
    public DecodedWorkerResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(payload, "payload");
    }
}
```

`WorkerContractCodec` exposes `encodeInspect(String requestId, InspectionRequest request)`,
`encodeBuildPlan(String requestId, PlanRequest request)`,
`encodeStageExecution(String requestId, StageExecutionRequest request)`, and
`decodeResponse(String requestId, WorkerOperation operation, String responseJson)`.
It uses a private `ObjectMapper` configured with
`JsonParser.Feature.STRICT_DUPLICATE_DETECTION`. It encodes the common
`schemaVersion`, `operation`, `requestId`, and `payload` envelope. It rejects
blank identifiers, unsupported schema versions, unknown operations, trailing
tokens, a mismatched request ID or operation, and a response with neither a
payload nor an error object. `encodeStageExecution` preserves every field in
`StageExecutionRequest`; `encodeInspect` and `encodeBuildPlan` use matching
application-port input values.

- [x] **Step 4: Re-run the codec tests and confirm they pass**

Run the command in Step 2. Expected: `WorkerContractCodecTest` passes with no failures or errors.

- [x] **Step 5: Commit codec work**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker `
  htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java
git commit -m "feat: add versioned worker contract codec"
```

### Task 2: Launch a Controlled Local Worker Process

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalProcessWorkerProperties.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerInvocation.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerInvocationResult.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerProcessLauncher.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncher.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncherTest.java`

- [x] **Step 1: Write failing launcher tests for response-file handoff and missing response failure**

```java
@Test
void writesRequestStartsConfiguredCommandAndReturnsResponseFile() {
    WorkerInvocationResult result = launcher.launch(new WorkerInvocation(
        "req-01", "{\"schemaVersion\":\"1.0\"}"
    ));

    assertThat(result.requestId()).isEqualTo("req-01");
    assertThat(result.responseJson()).contains("\"requestId\":\"req-01\"");
    assertThat(result.exitCode()).isZero();
}

@Test
void failsWhenSuccessfulProcessDoesNotWriteResponseFile() {
    assertThatThrownBy(() -> missingResponseLauncher.launch(new WorkerInvocation("req-02", "{}")))
        .isInstanceOf(WorkerProtocolException.class)
        .hasMessageContaining("response");
}
```

The tests use `java -cp <test-classpath> ...FixtureWorker` as the configured
command. The fixture Worker writes its response using the `--response` argument;
the missing-response fixture exits zero without creating it. Do not invoke
Python or any real Worker during unit tests.

- [x] **Step 2: Run the launcher tests and confirm they fail because the launcher is absent**

```powershell
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:/Users/24257/.m2/repository' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=LocalWorkerProcessLauncherTest' -DfailIfNoTests=false test
```

Expected: compilation fails because launcher and invocation types are absent.

- [x] **Step 3: Implement the launcher with isolated paths and timeout handling**

```java
public record LocalProcessWorkerProperties(List<String> command, Path workRoot, Duration timeout) {
    public LocalProcessWorkerProperties {
        command = List.copyOf(command);
        if (command.isEmpty() || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Worker command and positive timeout are required.");
        }
        workRoot = workRoot.toAbsolutePath().normalize();
    }
}

public interface WorkerProcessLauncher {
    WorkerInvocationResult launch(WorkerInvocation invocation);
    void cancel(String requestId);
}
```

`LocalWorkerProcessLauncher` creates `<workRoot>/<requestId>` only after
validating that the normalized child path starts with `workRoot`. It writes
`request.json`, appends `--request <path>` and `--response <path>` to a copied
configured command, redirects output to `stdout.log` and `stderr.log`, waits
only up to `timeout`, and removes a tracked process entry after it exits. It
throws `WorkerProtocolException` on timeout, nonzero exit, missing response,
or response-read failure. It never parses stdout or stderr as a result.

- [x] **Step 4: Re-run the launcher tests and confirm they pass**

Run the command in Step 2. Expected: successful response handoff and missing
response behavior both pass; the test work root is removed in `@AfterEach`.

- [x] **Step 5: Commit process-launcher work**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker `
  htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncherTest.java
git commit -m "feat: launch local workers through response files"
```

### Task 3: Translate Worker Results Through Application Ports

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerResultTranslator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalProcessWorkerGateway.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalProcessWorkerGatewayTest.java`

- [x] **Step 1: Write failing gateway tests for all three application ports**

```java
@Test
void inspectionGatewayReturnsAllCandidatesFromVerifiedResponse() {
    LocalProcessWorkerGateway gateway = gatewayReturning(inspectResponseJson());

    InspectionResponse response = gateway.inspect(inspectRequest());

    assertThat(response.rulePackageVersion()).isEqualTo("rules-2026.08");
    assertThat(response.candidates()).hasSize(2);
}

@Test
void plannerGatewayRebuildsValidatedProcessingPlan() {
    ProcessingPlan plan = gatewayReturning(planResponseJson()).buildPlan(planRequest());

    assertThat(plan.version()).isEqualTo("plan-v1");
    assertThat(plan.stages()).extracting(ProcessingStage::stageKey)
        .containsExactly("READ", "CAL");
}

@Test
void dispatcherGatewayReturnsWorkerIdWithoutPersistingTaskState() {
    DispatchReceipt receipt = gatewayReturning(stageResponseJson()).dispatch(stageRequest());

    assertThat(receipt.workerId()).isEqualTo("worker-local-01");
}
```

Use a test `WorkerProcessLauncher` that returns prepared response JSON. Assert
the launcher was called with the expected operation and assert no repository is
accepted by the gateway constructor.

- [x] **Step 2: Run the gateway tests and confirm they fail because translator and gateway are absent**

```powershell
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:/Users/24257/.m2/repository' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=LocalProcessWorkerGatewayTest' -DfailIfNoTests=false test
```

Expected: compilation fails because `WorkerResultTranslator` and
`LocalProcessWorkerGateway` do not exist.

- [x] **Step 3: Implement result translation and the port adapter**

```java
public final class LocalProcessWorkerGateway
        implements ProductInspector, ProcessingPlanner, TaskDispatcher {

    @Override
    public InspectionResponse inspect(InspectionRequest request) {
        String requestId = requestIdSupplier.get();
        return translator.toInspection(invoke(
            requestId, WorkerOperation.INSPECT, codec.encodeInspect(requestId, request)));
    }

    @Override
    public ProcessingPlan buildPlan(PlanRequest request) {
        String requestId = requestIdSupplier.get();
        return translator.toPlan(invoke(
            requestId, WorkerOperation.BUILD_PLAN, codec.encodeBuildPlan(requestId, request)));
    }

    @Override
    public DispatchReceipt dispatch(StageExecutionRequest request) {
        String requestId = requestIdSupplier.get();
        runningRequests.put(request.executionId(), requestId);
        try {
            return translator.toDispatchReceipt(invoke(
                requestId, WorkerOperation.EXECUTE_STAGE, codec.encodeStageExecution(requestId, request)));
        } finally {
            runningRequests.remove(request.executionId(), requestId);
        }
    }

    @Override
    public void cancel(ExecutionId executionId) {
        String requestId = runningRequests.remove(executionId);
        if (requestId != null) {
            launcher.cancel(requestId);
        }
    }

    private DecodedWorkerResponse invoke(String requestId, WorkerOperation operation, String requestJson) {
        WorkerInvocationResult result = launcher.launch(new WorkerInvocation(requestId, requestJson));
        return codec.decodeResponse(requestId, operation, result.responseJson());
    }
}
```

The gateway receives an injected non-blank `Supplier<String>` request-ID source
and passes its generated ID through codec, launcher, and decoder. For `inspect`,
that envelope ID is also the preflight identifier. `WorkerResultTranslator` creates only existing domain
values (`ProductCandidate`, `ProcessingPlan`, `ProcessingStage`, and
`DispatchReceipt`) and lets their constructors enforce validation. A valid
Worker error response raises a dedicated `WorkerProtocolException` containing
the Worker error code and retryable flag. `cancel(ExecutionId)` delegates only
to the tracked local invocation mapping; it performs no persistence.

- [x] **Step 4: Re-run gateway tests and confirm they pass**

Run the command in Step 2. Expected: all three port adapter behaviors pass,
and no test depends on PostgreSQL or a real worker executable.

- [x] **Step 5: Commit gateway work**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker `
  htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalProcessWorkerGatewayTest.java
git commit -m "feat: adapt local worker to multisource ports"
```

### Task 4: Verify Failure Boundaries and Synchronize Documentation

**Files:**
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncherTest.java`
- Modify: `docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md`
- Modify: `docs/superpowers/plans/2026-08-20-local-worker-gateway-plan.md`

- [x] **Step 1: Write focused failing tests for malformed and stale transport data**

```java
@ParameterizedTest
@ValueSource(strings = {
    "{\"schemaVersion\":\"2.0\",\"operation\":\"inspect\",\"requestId\":\"req-01\",\"payload\":{}}",
    "{\"schemaVersion\":\"1.0\",\"operation\":\"inspect\",\"requestId\":\"other\",\"payload\":{}}",
    "not-json"
})
void rejectsInvalidOrMismatchedResponse(String response) {
    assertThatThrownBy(() -> codec.decodeResponse("req-01", WorkerOperation.INSPECT, response))
        .isInstanceOf(WorkerProtocolException.class);
}
```

Add a launcher test where the fixture worker sleeps beyond the configured
timeout. Assert the exception identifies the invocation and that the tracked
process is removed after termination.

- [x] **Step 2: Run focused tests and confirm the new failure cases fail for the expected missing checks**

```powershell
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:/Users/24257/.m2/repository' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=WorkerContractCodecTest,LocalWorkerProcessLauncherTest,LocalProcessWorkerGatewayTest' `
  -DfailIfNoTests=false test
```

Expected: new tests fail only until schema validation, request matching, and
timeout cleanup are implemented.

- [x] **Step 3: Implement only the missing validations and cleanup**

Reject all response envelope mismatches before translation. On timeout call
`destroy()`, wait briefly, then call `destroyForcibly()` only when necessary;
always remove the request from the running-process map in `finally`. Keep
request/response diagnostic files for inspection and do not add repository,
controller, or database writes.

- [x] **Step 4: Run the Worker suite and existing Slice A regression**

```powershell
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:/Users/24257/.m2/repository' `
  -pl htht-modules\htht-multisource -am `
  '-Dtest=WorkerContractCodecTest,LocalWorkerProcessLauncherTest,LocalProcessWorkerGatewayTest,PreflightProductUseCaseTest,CreateProcessingTaskUseCaseTest,GetTaskDetailUseCaseTest' `
  -DfailIfNoTests=false test
```

Expected: zero failures and errors. This suite must not set
`MULTISOURCE_IT_*` or write to the development database.

- [x] **Step 5: Mark Task 6 progress and commit verification/documentation**

Mark Task 6 Steps 1-5 complete in both plan documents. Do not mark execution
or product-adapter tasks complete. Then commit:

```powershell
git add docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md `
  docs/superpowers/plans/2026-08-20-local-worker-gateway-plan.md `
  htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker
git commit -m "test: verify local worker gateway boundaries"
```
