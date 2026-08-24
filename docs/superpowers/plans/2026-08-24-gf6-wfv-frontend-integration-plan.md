# GF-6 WFV Frontend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current mock multi-source satellite panel with a real GF-6 WFV product-template workflow that uploads JPG/JGW/JSON files, creates and starts one authorized task, polls its four stages, and downloads `result.zip`.

**Architecture:** Keep product rules and local validation in a framework-independent TypeScript template module. Keep HTTP DTOs and endpoint paths in the API module. The Vue component renders fixed upload slots and task states; it does not construct role rules or call Axios directly. The browser uses `taskId` and backend status as the source of truth and never drives processing by repeatedly calling the single-stage `dispatch` endpoint.

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus, Axios through the existing `http` wrapper, pnpm, Vite.

---

## Preconditions and Backend Contract

The frontend work depends on these backend contracts being available in the deployed backend:

```text
POST /system/file/uploadStatic
POST /api/multisource/processing-tasks/preflight
POST /api/multisource/processing-tasks
POST /api/multisource/processing-tasks/{taskId}/start
GET  /api/multisource/processing-tasks/{taskId}/progress
POST /api/multisource/processing-tasks/{taskId}/cancel
POST /api/multisource/processing-stages/{stageId}/retry
GET  /api/multisource/processing-tasks/{taskId}/result.zip
```

The existing Java DTOs require these request shapes:

```ts
interface AssetRequest {
  sourceType: "SYS_FILE";
  sourceId: string | number;
  role: "BROWSE_IMAGE" | "GEOREFERENCE" | "METADATA";
  ordinal: 0;
  required: true;
}

interface PreflightRequest {
  assets: AssetRequest[];
}

interface CreateTaskRequest {
  name: string;
  preflightToken: string;
  candidateId: string;
  parameterSnapshot: string;
}
```

`parameterSnapshot` must be sent as:

```ts
JSON.stringify({ schemaVersion: "1", operation: "DEFAULT_PROCESS" });
```

The frontend implementation must stop at a clear configuration error if `/start` or `/result.zip` is unavailable; it must not silently fall back to the old mock endpoint.

## File Structure

| File | Change | Responsibility |
| --- | --- | --- |
| `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/productTemplates.ts` | Create | GF-6 product template, fixed slot definitions, parameter snapshot, basename/extension validation. |
| `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts` | Replace | Typed upload, preflight, create, start, progress, cancel, retry and ZIP download calls. |
| `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts` | Create | Upload orchestration, task state, polling timer lifecycle, cancellation and retry actions. |
| `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue` | Modify | Render product selector, three fixed slots, preflight candidate, task stages and result actions. Remove free-form operation UI and mock result flow. |
| `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/package.json` | Modify only if needed | Do not add a test framework for this narrow MVP; retain existing `pnpm typecheck` and `pnpm build` gates. |

## Task 1: Add the GF-6 Product Template and Pure Validation

**Files:**

- Create: `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/productTemplates.ts`
- Modify: `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`

- [ ] **Step 1: Define the template types and fixed snapshot.**

Add these declarations:

```ts
export type AssetRole = "BROWSE_IMAGE" | "GEOREFERENCE" | "METADATA";
export type SlotId = "browseImage" | "georeference" | "metadata";

export interface ProductSlot {
  id: SlotId;
  label: string;
  role: AssetRole;
  extensions: readonly string[];
  missingMessage: string;
}

export interface ProductTemplate {
  id: string;
  label: string;
  productCode: "GF-6-WFV-BROWSE";
  profileVersion: "v1";
  slots: readonly ProductSlot[];
  parameterSnapshot: { schemaVersion: "1"; operation: "DEFAULT_PROCESS" };
}

export const DEFAULT_PROCESS_SNAPSHOT = {
  schemaVersion: "1",
  operation: "DEFAULT_PROCESS"
} as const;
```

- [ ] **Step 2: Add the single visible GF template and validation helpers.**

The template must contain exactly these slots:

```ts
export const GF6_WFV_BROWSE_TEMPLATE: ProductTemplate = {
  id: "gf6-wfv-browse-v1",
  label: "GF-6 WFV 浏览产品",
  productCode: "GF-6-WFV-BROWSE",
  profileVersion: "v1",
  parameterSnapshot: DEFAULT_PROCESS_SNAPSHOT,
  slots: [
    { id: "browseImage", label: "浏览影像", role: "BROWSE_IMAGE", extensions: [".jpg", ".jpeg"], missingMessage: "缺少浏览影像（JPG/JPEG）" },
    { id: "georeference", label: "地理参考文件", role: "GEOREFERENCE", extensions: [".jgw"], missingMessage: "缺少地理参考文件（JGW）" },
    { id: "metadata", label: "产品元数据", role: "METADATA", extensions: [".json"], missingMessage: "缺少产品元数据（JSON）" }
  ]
};

export function basenameWithoutExtension(name: string): string {
  return name.replace(/\.[^.]+$/, "");
}

export function validateProductFiles(files: Partial<Record<SlotId, File>>): string[] {
  const errors = GF6_WFV_BROWSE_TEMPLATE.slots.flatMap(slot => {
    const file = files[slot.id];
    if (!file) return [slot.missingMessage];
    const lowerName = file.name.toLowerCase();
    if (!slot.extensions.some(ext => lowerName.endsWith(ext))) {
      return [`${slot.label}格式错误，仅支持${slot.extensions.join("、")}`];
    }
    return [];
  });
  const selected = GF6_WFV_BROWSE_TEMPLATE.slots
    .map(slot => files[slot.id])
    .filter((file): file is File => Boolean(file));
  if (selected.length === 3) {
    const names = new Set(selected.map(file => basenameWithoutExtension(file.name)));
    if (names.size !== 1) errors.push("三个文件的产品编号必须一致");
  }
  return errors;
}
```

- [ ] **Step 3: Run the existing frontend compiler against the new module.**

Run from `D:\Lwy_project_space\RSImageProcess\rsimageprocess-view`:

```powershell
pnpm typecheck
```

Expected: no new diagnostics in `productTemplates.ts`. Existing unrelated diagnostics in `src/components/ReAuth/src/auth.tsx` and `src/libs/*` may remain and must be recorded rather than fixed in this feature.

- [ ] **Step 4: Commit the template boundary.**

```powershell
git add src/features/multisource/productTemplates.ts src/components/LeftPanel/MultiSourceSatelliteProcess.vue
git commit -m "feat: define GF6 browse product template"
```

## Task 2: Replace Mock API Calls with Typed Real Endpoints

**Files:**

- Replace: `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts`

- [ ] **Step 1: Add typed request/response contracts before implementation.**

Define these types in the API module:

```ts
export interface SysFileUploadResponse { fileId: string | number }
export interface PreflightResponse {
  rulePackageVersion: string;
  preflightToken: string;
  expiresAt: string;
  candidates: Array<{
    id: string;
    packageId: string;
    supportStatus: string;
    confidence: number;
  }>;
}
export interface TaskIdResponse { taskId: number }
export interface StartResponse { newlySubmitted: boolean }
export interface ExecutionProgressResponse {
  taskId: number;
  lifecycleStatus: "CREATED" | "RUNNING" | "RECOVERY_PENDING" | "SUCCEEDED" | "FAILED" | "CANCELED";
  qualityStatus: string;
  stages: Array<{ stageId: number; stageKey: string; status: string; attempt: number }>;
  artifacts: Array<{ stageId: number; type: string; uri: string; checksum: string }>;
}
```

- [ ] **Step 2: Write the real endpoint functions.**

Use the existing `http` wrapper and `Result<T>` response convention:

```ts
export function uploadStaticFile(file: File) {
  const form = new FormData();
  form.append("file", file);
  return http.request<Result<SysFileUploadResponse>>("post", "/system/file/uploadStatic", { data: form });
}

export const preflightProcessingTask = (data: PreflightRequest) =>
  http.request<Result<PreflightResponse>>("post", "/api/multisource/processing-tasks/preflight", { data });

export const createProcessingTask = (data: CreateTaskRequest) =>
  http.request<Result<TaskIdResponse>>("post", "/api/multisource/processing-tasks", { data });

export const startProcessingTask = (taskId: number) =>
  http.request<Result<StartResponse>>("post", `/api/multisource/processing-tasks/${taskId}/start`);

export const getProcessingProgress = (taskId: number) =>
  http.request<Result<ExecutionProgressResponse>>("get", `/api/multisource/processing-tasks/${taskId}/progress`);

export const cancelProcessingTask = (taskId: number) =>
  http.request<Result<void>>("post", `/api/multisource/processing-tasks/${taskId}/cancel`);

export const retryProcessingStage = (stageId: number) =>
  http.request<Result<void>>("post", `/api/multisource/processing-stages/${stageId}/retry`);

export const downloadProcessingResult = (taskId: number) =>
  http.request<Blob>("get", `/api/multisource/processing-tasks/${taskId}/result.zip`, { responseType: "blob" });
```

Delete `runMultiSourceSatelliteProcess`, `downloadMultiSourceSatelliteResult`, all `.rar` types, and all `/multi-source-satellite/*` paths. Do not add a fallback to the mock service.

- [ ] **Step 3: Run typecheck and search for stale mock contracts.**

Run:

```powershell
pnpm typecheck
Select-String -Path src -Recurse -Pattern '/multi-source-satellite|\.rar|runMultiSourceSatelliteProcess|selectedOperations'
```

Expected: the search returns no source references to the old process/download contract; typecheck has no diagnostics caused by the API module.

- [ ] **Step 4: Commit the typed API client.**

```powershell
git add src/api/multiSourceSatellite.ts
git commit -m "feat: add multisource processing API client"
```

## Task 3: Implement Task Lifecycle and Polling Composable

**Files:**

- Create: `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts`

- [ ] **Step 1: Define the composable state and action signatures.**

```ts
export type SlotStatus = "EMPTY" | "SELECTED" | "UPLOADING" | "UPLOADED" | "FAILED";
export type TaskStatus = ExecutionProgressResponse["lifecycleStatus"];

export interface SlotState {
  status: SlotStatus;
  file?: File;
  sysFileId?: string | number;
  error?: string;
}

export function useProcessingTask() {
  // returns slots, preflight, taskId, taskStatus, progress,
  // uploadSlot, runPreflight, createAndStart, cancel, retry, stopPolling, reset
}
```

- [ ] **Step 2: Implement upload and preflight sequencing.**

`uploadSlot(slotId, file)` must validate the selected slot extension, set `UPLOADING`, call `uploadStaticFile`, save the returned `fileId`, and set `UPLOADED` or `FAILED`. Any file replacement must clear preflight token, candidate, task ID, task status, progress, and polling timer.

`runPreflight()` must build exactly three assets from the template, reject local diagnostics, call the preflight endpoint, and accept only one candidate whose `supportStatus` is `SUPPORTED` and whose package/profile is GF-6 WFV browse. It must retain `preflightToken` and `candidate.id` for task creation.

- [ ] **Step 3: Implement create/start and bounded polling.**

`createAndStart()` must use the selected basename as `name`, call create with `JSON.stringify(DEFAULT_PROCESS_SNAPSHOT)`, store `taskId`, call `/start` once, then begin polling. Poll at 2 seconds while responses change; after 10 unchanged responses use 5 seconds. Stop polling for every terminal status and on unmount, reset, file replacement, task switch, or cancel.

- [ ] **Step 4: Implement cancellation, retry, and ZIP download helpers.**

Cancellation calls the task endpoint and stops local start actions; the final state comes from the next progress response. Retry accepts only the failed stage ID returned by progress and calls `retryProcessingStage`; after a successful retry, call `createAndStart` only if the task remains resumable and the backend contract explicitly permits it. Download creates a Blob URL, uses `Content-Disposition` when available, falls back to `gf6-wfv-result.zip`, and revokes the URL after clicking.

- [ ] **Step 5: Run typecheck for the composable.**

Run:

```powershell
pnpm typecheck
```

Expected: no new diagnostics from `useProcessingTask.ts` or `multiSourceSatellite.ts`.

- [ ] **Step 6: Commit the lifecycle composable.**

```powershell
git add src/features/multisource/useProcessingTask.ts
git commit -m "feat: manage multisource task lifecycle"
```

## Task 4: Replace the Panel UI with the Template Workflow

**Files:**

- Modify: `D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`

- [ ] **Step 1: Remove the old free-form template and script state.**

Remove the old upload list, satellite selector, operation checkbox list, fake processing result, and RAR download handler. Import only the GF template, validation helpers, API-independent composable, and the required Element Plus icons.

- [ ] **Step 2: Render the product selector and three fixed upload slots.**

The selector must contain only `GF-6 WFV 浏览产品`. Each slot must show label, accepted extensions, selected filename, status, retry/remove action, and the exact missing or extension diagnostic. The preflight button is disabled until all three slots are uploaded and `validateProductFiles()` returns an empty array.

- [ ] **Step 3: Render preflight candidate and task actions.**

After preflight, show the recognized package ID and support status. Enable create/start only for exactly one supported GF-6 candidate. Do not render an operation list or expose `role`, `ordinal`, `SYS_FILE`, worker command, physical path, output URI, or stage graph inputs.

- [ ] **Step 4: Render progress, failure, retry, cancellation, and result states.**

Show the four fixed stage keys and each stage status/attempt. Show cancel only for `CREATED`/`RUNNING`. Show retry only for the backend-selected failed stage. Show download only for `SUCCEEDED`. Show “重新处理” by clearing task and preflight context while retaining no stale file IDs after a replacement.

- [ ] **Step 5: Run compiler/build and inspect the rendered workflow.**

Run:

```powershell
pnpm typecheck
pnpm build
```

Expected: the feature files introduce no new diagnostics; the build completes. Existing unrelated project diagnostics must be recorded separately. Start the dev server with `pnpm dev -- --host 127.0.0.1 --port 8848` and manually verify that the page no longer displays satellite-type selectors, operation checkboxes, mock processing text, or RAR download text.

- [ ] **Step 6: Commit the panel integration.**

```powershell
git add src/components/LeftPanel/MultiSourceSatelliteProcess.vue
git commit -m "feat: connect GF6 template task workflow"
```

## Task 5: Cross-Repository Contract Verification

**Files:**

- Modify only if necessary: `D:/Lwy_project_space/RSImageProcess/JavaRSproject/docs/superpowers/specs/2026-08-23-gf6-wfv-mvp-frontend-backend-design.md`
- Modify: `D:/Lwy_project_space/RSImageProcess/JavaRSproject/docs/superpowers/plans/2026-08-24-gf6-wfv-frontend-integration-plan.md`

- [ ] **Step 1: Verify API request payloads with an authenticated backend.**

Confirm in browser network tools:

```text
uploadStaticFile -> each file receives a positive sysFileId
preflight -> assets contains exactly BROWSE_IMAGE/GEOREFERENCE/METADATA
create -> parameterSnapshot is the JSON string for DEFAULT_PROCESS
start -> HTTP 202 and no request body
progress -> stages are returned in DAG order
result.zip -> HTTP 200, application/zip
```

- [ ] **Step 2: Run the real GF sample acceptance flow.**

Use either product package, but keep its three files together:

```text
D:\Lwy_project_space\RSImageProcess\otherfile\GF\GF6-WFV-LEVEL1A-1420733151.jpg
D:\Lwy_project_space\RSImageProcess\otherfile\GF\GF6-WFV-LEVEL1A-1420733151.jgw
D:\Lwy_project_space\RSImageProcess\otherfile\GF\GF6-WFV-LEVEL1A-1420733151.json
```

Verify that a missing slot, wrong extension, or different basename blocks preflight; a valid package creates one task; the server continues after page refresh; the UI reaches `SUCCEEDED`; and the ZIP contains `browse-report.json` without raw inputs or physical paths.

- [ ] **Step 3: Run the minimal release gates.**

Run from the frontend repository:

```powershell
pnpm typecheck
pnpm build
```

Run from the backend repository only after its `start` and ZIP endpoints are merged:

```powershell
mvn -pl htht-modules/htht-multisource,htht-admin -am test
```

Record existing unrelated frontend type errors separately; do not mark the frontend integration complete if any error references the newly changed files.

- [ ] **Step 4: Commit only final plan evidence if it changed.**

```powershell
git add docs/superpowers/plans/2026-08-24-gf6-wfv-frontend-integration-plan.md
git commit -m "docs: add GF6 frontend integration plan"
```

## Acceptance Checklist

- [ ] Product selector shows only GF-6 WFV browse product.
- [ ] Three fixed slots map JPG/JPEG, JGW, and JSON to the correct backend roles.
- [ ] Missing files, extension mismatch, and basename mismatch block preflight.
- [ ] Each upload stores a backend `sysFileId`; the browser never sends a physical path.
- [ ] Preflight requires exactly one supported GF-6 candidate.
- [ ] Create sends the exact `DEFAULT_PROCESS` snapshot as a JSON string.
- [ ] Start is called once; the browser never loops over `/dispatch`.
- [ ] Polling stops on terminal state, cancellation, reset, replacement, unmount, or task switch.
- [ ] Failure/retry UI uses stage ID and attempt count from backend state.
- [ ] Successful tasks download `result.zip` as a Blob.
- [ ] No source code references the mock process endpoint, `.rar`, free-form satellite type, or operation selector.
- [ ] Frontend typecheck/build introduces no new errors in changed files, and authenticated GF sample acceptance succeeds.

## Plan Self-Review

**Spec coverage:** Task 1 covers product templates, roles, extensions, basename rules, and fixed parameters. Task 2 covers every real HTTP endpoint and the actual Java DTO field names. Task 3 covers upload state, task state, polling, cancellation, retry, and download mechanics. Task 4 covers the user-facing panel and removal of mock controls. Task 5 covers authenticated contract and real-sample acceptance.

**Placeholder scan:** No deferred implementation markers, vague validation directives, or references to undefined task names are used. Every task names files, commands, expected results, and commit scope.

**Type consistency:** `AssetRole`, `SlotId`, `DEFAULT_PROCESS_SNAPSHOT`, `PreflightResponse`, `TaskIdResponse`, `ExecutionProgressResponse`, `TaskStatus`, `packageId`, `candidateId`, `preflightToken`, and all endpoint paths are used consistently throughout the plan.
