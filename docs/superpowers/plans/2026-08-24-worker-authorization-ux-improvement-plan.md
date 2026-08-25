# Worker Authorization UX Improvement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GF-6 processing resilient to short-lived Worker authorization and present one clear "开始处理" workflow instead of exposing Worker protocol errors to users.

**Architecture:** Keep the existing preflight, task creation, and stage execution boundaries. Preflight remains an optional diagnostic endpoint, but the primary frontend action invokes preflight immediately before task creation. Every Worker operation receives a fresh grant; if the Worker explicitly reports an expired manifest, the backend regenerates the grant and retries that operation once. The launcher will preserve the Worker response on non-zero exit so the API can return a stable business error instead of only an exit code.

**Tech Stack:** Spring Boot 2.7, Java 17, existing multisource application ports/use cases, local Python Worker protocol, Vue 3, TypeScript, Element Plus.

---

## Scope and invariants

- Do not extend the grant TTL as the primary fix; the grant must be created immediately before each Worker invocation.
- Do not persist a Worker grant in the preflight session. The session stores the selected candidate and input snapshot only.
- Retry only the known recoverable condition `INVALID_ASSET_MANIFEST` with message `manifest is expired`, and retry at most once per Worker operation.
- Keep the existing API paths for compatibility. The frontend changes its main action sequencing; no new public endpoint is required for this MVP.
- A failed retry must return a user-facing message and retain the request id in server logs.

### Task 1: Preserve Worker error payloads

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncher.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerProtocolException.java`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/FixtureWorker.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/LocalWorkerProcessLauncherTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

- [ ] **Step 1: Add a failing launcher test.** Add an `invalid-manifest` mode to `FixtureWorker` that writes a valid `INVALID_ASSET_MANIFEST` response and exits with code `2`. Configure the fixture launcher to use that mode, then assert that `launch` returns the response JSON and exit code instead of throwing only `process exited with code 2`.

```java
@Test
void returnsWorkerResponseWhenProcessExitsWithProtocolError() {
    LocalWorkerProcessLauncher launcher = launcher("invalid-manifest", Duration.ofSeconds(2));
    WorkerInvocationResult result = launcher.launch(invocation("inspect"));

    assertThat(result.exitCode()).isEqualTo(2);
    assertThat(result.responseJson()).contains("INVALID_ASSET_MANIFEST");
}
```

- [ ] **Step 2: Run the focused test and verify it fails** with the current launcher message `process exited with code 2`.

```powershell
mvn -pl htht-modules/htht-multisource -Dtest=LocalWorkerProcessLauncherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Change launcher ordering.** After the process exits, read `response.json` when present. Return `WorkerInvocationResult(requestId, exitCode, responseJson)` even when `exitCode != 0`; throw only when the response is missing or unreadable. Keep timeout and process-start failures unchanged.

```java
int exitCode = process.exitValue();
if (!Files.isRegularFile(responseFile)) {
    throw protocolFailure(invocation, "response file was not written (exit code " + exitCode + ")", null);
}
String responseJson = Files.readString(responseFile, StandardCharsets.UTF_8);
return new WorkerInvocationResult(invocation.requestId(), exitCode, responseJson);
```

- [ ] **Step 4: Add structured Worker error fields.** Extend `WorkerProtocolException` with `code` and `retryable` fields and add `isManifestExpired()` returning true only for code `INVALID_ASSET_MANIFEST` and message `manifest is expired`. In `WorkerContractCodec.decodeResponse`, construct this exception from the Worker error object instead of flattening it into a string.

- [ ] **Step 5: Run the focused launcher and codec tests** and verify they pass. Existing malformed-response tests must still fail with a protocol exception.

- [ ] **Step 6: Commit the backend boundary change.**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker
git commit -m "fix: preserve worker protocol error responses"
```

### Task 2: Refresh grants and retry expiration once

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/PreflightProductService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinator.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/PreflightProductUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CreateProcessingTaskUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`

- [ ] **Step 1: Add a failing preflight retry test in `PreflightProductUseCaseTest.java`.** Use the test's existing fake `ProductInspector` and `AssetStorage` implementations. Configure the inspector to throw `WorkerProtocolException` with `INVALID_ASSET_MANIFEST / manifest is expired` on the first call and return the fixture candidate on the second. Assert that `AssetStorage.grantRead` is called twice and preflight succeeds.

```java
@Test
void refreshesReadGrantOnceWhenManifestExpires() {
    // Configure the existing fakes as described above, invoke preflight(...),
    // then assert the two call counts and the returned fixture candidate.
}
```

- [ ] **Step 2: Run the focused service test and verify the expected failure** before implementation.

- [ ] **Step 3: Implement a single retry in `PreflightProductService`.** Copy the input list once, request a new grant for the first attempt, and on `isManifestExpired()` request a new grant and invoke the inspector once more. Propagate all other exceptions immediately.

```java
private InspectionResponse inspectWithFreshGrant(AccessSubject subject, List<InputAsset> inputs) {
    try {
        return inspector.inspect(new InspectionRequest(subject, inputs, assetStorage.grantRead(subject, inputs)));
    } catch (WorkerProtocolException error) {
        if (!error.isManifestExpired()) {
            throw error;
        }
        return inspector.inspect(new InspectionRequest(subject, inputs, assetStorage.grantRead(subject, inputs)));
    }
}
```

- [ ] **Step 4: Apply the same fresh-grant/retry helper to `TaskCreationCoordinator` around `planner.buildPlan`.** Task creation must never reuse the preflight grant; it already receives the candidate inputs, so a new grant is generated immediately before planning.

- [ ] **Step 5: Add one execution-stage retry in `DispatchNextStageService`.** Keep the existing `assetStorage.grant(new ExecutionGrantRequest(...))` call immediately before dispatch. Extract a helper with the concrete signature `private DispatchReceipt dispatchWithRefresh(Supplier<StageExecutionRequest> requestFactory)`. The factory must create a new execution grant and a new `StageExecutionRequest` on every call. If the first dispatch throws `WorkerProtocolException.isManifestExpired()`, call the factory and dispatcher once more for the same claimed stage/execution attempt. Do not emit a failure event between the two attempts; emit one failure event only if the second attempt fails.

```java
private DispatchReceipt dispatchWithRefresh(Supplier<StageExecutionRequest> requestFactory) {
    try {
        return dispatcher.dispatch(requestFactory.get());
    } catch (WorkerProtocolException error) {
        if (!error.isManifestExpired()) {
            throw error;
        }
        return dispatcher.dispatch(requestFactory.get());
    }
}
```

The call site must pass a lambda that invokes `assetStorage.grant(new ExecutionGrantRequest(task.id(), claim.stage().id(), executionId, claim.stage().attempt(), candidate.files(), task.outputUri()))` and constructs the full `StageExecutionRequest` with that grant. Both requests therefore carry the same task id, stage id, execution id, and attempt while using different grant references.

- [ ] **Step 6: Run only the affected multisource service tests.** Expected result: all focused tests pass; no full suite is required in this iteration.

- [ ] **Step 7: Commit the grant lifecycle change.**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase
git commit -m "fix: refresh expired worker grants once"
```

### Task 3: Return stable backend error messages

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/MultisourceExceptionHandler.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerProtocolException.java` if not completed in Task 1
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/MultisourceExceptionHandlerTest.java`

- [ ] **Step 1: Add a failing exception-handler test in `MultisourceExceptionHandlerTest.java`** asserting that a final expired-manifest failure returns a stable message such as `文件授权已过期，请重新点击开始处理` and logs the request id, while the response does not expose the raw command line or local path.

- [ ] **Step 2: Add a multisource-scoped `@RestControllerAdvice`.** Create `MultisourceExceptionHandler` in `htht-admin`, where the admin module already depends on the multisource module. Map `WorkerProtocolException` to `R.fail(...)`. Use `文件授权已过期，请重新点击开始处理` for `isManifestExpired()`, `Worker 执行失败，请稍后重试` for other Worker errors, and keep the detailed exception in the server log. Do not add a dependency from the shared `htht-common-framework` module to multisource.

```java
@RestControllerAdvice(basePackages = "com.htht.web.controller.multisource")
public final class MultisourceExceptionHandler {
    @ExceptionHandler(WorkerProtocolException.class)
    public R<Void> handleWorkerProtocolException(WorkerProtocolException error, HttpServletRequest request) {
    log.error("请求地址'{}' Worker 调用失败: {}", request.getRequestURI(), error.getMessage(), error);
    return R.fail(error.isManifestExpired()
        ? "文件授权已过期，请重新点击开始处理"
        : "Worker 执行失败，请稍后重试");
    }
}
```

- [ ] **Step 3: Verify `MultisourceExceptionHandlerTest`** checks the user-facing message and that the raw `process exited with code 2` text is absent.

- [ ] **Step 4: Commit the API error mapping.**

```powershell
git add htht-admin/src/main/java/com/htht/web/controller/multisource/MultisourceExceptionHandler.java htht-admin/src/test/java/com/htht/web/controller/multisource/MultisourceExceptionHandlerTest.java
git commit -m "fix: expose friendly worker processing errors"
```

### Task 4: Make the frontend start action the primary workflow

**Files:**
- Modify: `rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts` only if the backend error response type requires it

- [ ] **Step 1: Change the composable contract.** Add `startProcessing()` that performs `runPreflight()` when there is no current candidate, then creates the task and starts it. Keep `runPreflight()` exported for the optional diagnostic button.

```ts
async function startProcessing() {
  if (!candidate.value || !preflight.value) {
    await runPreflight();
  }
  return createAndStart();
}
```

- [ ] **Step 2: Update the page controls.** Change the first action label from `预检产品` to `开始处理`; call `startProcessing` from the main button. Move `预检产品` to a text/secondary action only if a diagnostic action is still needed. Disable both while upload or request state is active. The confirmation section should become an informational status, not a required second click.

- [ ] **Step 3: Add error translation in the composable/page.** Map these backend messages to stable UI copy:

```ts
const workerErrorMessages: Record<string, string> = {
  "文件授权已过期，请重新点击开始处理": "文件检查已过期，系统会自动重新检查",
  "Worker 执行失败，请稍后重试": "处理服务暂时不可用，请稍后重试"
};
```

Show `正在检查文件...` during the automatic preflight and `正在启动处理...` during task creation/start. On final failure show `开始处理失败，请重试`; preserve the request id in the browser console only when available.

- [ ] **Step 4: Make retry/reset deterministic.** On a final preflight or start failure, clear only the stale candidate/preflight session, retain uploaded slots, and expose a `重新开始` action that invokes `startProcessing()` again. Do not force the user to re-upload valid files.

- [ ] **Step 5: Run the frontend build and targeted static checks.** No new frontend test runner is present in this repository, so use the existing build/type checks and inspect the changed composable for TypeScript errors.

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm build
pnpm exec vue-tsc --noEmit --skipLibCheck 2>&1 | Select-String -Pattern 'src/features/multisource/useProcessingTask.ts|src/components/LeftPanel/MultiSourceSatelliteProcess.vue|src/api/multiSourceSatellite.ts'
```

- [ ] **Step 6: Commit the frontend workflow change.**

```powershell
git add src/features/multisource/useProcessingTask.ts src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/api/multiSourceSatellite.ts
git commit -m "feat: streamline multisource processing start flow"
```

### Task 5: End-to-end verification

**Files:**
- Modify: `docs/deployment/multisource-backend-deployment.md` to document Worker grant behavior and the single retry.
- Test data: `D:/Lwy_project_space/RSImageProcess/otherfile/GF`

- [ ] **Step 1: Rebuild the backend without running the full test suite.**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -Pdev -pl :htht-admin -am -DskipTests package
```

- [ ] **Step 2: Start PostgreSQL, Redis, Worker environment, backend, and frontend using the existing documented commands.** Confirm backend `http://127.0.0.1:8080/` and frontend `http://127.0.0.1:8848/` respond successfully.

- [ ] **Step 3: Perform one manual GF-6 flow.** Upload the JPG, JGW, and JSON sample, click only `开始处理`, wait at least six minutes before clicking nothing else, and verify the system either completes without a stale-grant error or automatically retries and completes. Verify the result ZIP download.

- [ ] **Step 4: Perform one failure-message check.** Force an expired manifest fixture in the Worker integration test or wait for the existing five-minute grant to expire; verify the UI shows a Chinese recovery message and never shows `process exited with code 2`.

- [ ] **Step 5: Stop both services and verify ports 8080 and 8848 are released.**

- [ ] **Step 6: Commit documentation and run final repository checks.**

```powershell
git diff --check
git status --short
git log -1 --oneline
```

## Self-review

- Expired preflight grants are regenerated instead of being hidden by a longer TTL.
- Task planning and stage execution use fresh grants independently of the preflight session.
- Non-zero Worker exits with a response are decoded, so business error codes remain visible to the backend.
- The frontend has one primary action and retains uploaded files after recoverable failure.
- Verification is limited to focused backend tests, one frontend build/static check, and one manual GF flow.
