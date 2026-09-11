# Multisource Artifact Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dual-section task progress dialog and safe, format-aware viewing for intermediate and final processing artifacts.

**Architecture:** The progress response declares format and view capability. The dialog renders execution detail separately from a task-wide artifact list. Java authorizes artifact inspection and tile tickets; the Python Worker performs bounded file inspection and raster rendering.

**Tech Stack:** Vue 3, TypeScript, Cesium, Spring Boot, Java 17, Python 3.11, rasterio, netCDF4, pyarrow.

**Spec:** `docs/superpowers/specs/2026-09-11-multisource-artifact-experience-design.md`

## Global Constraints

- Keep task ownership checks and existing artifact download authorization.
- Do not expose filesystem paths, bearer tokens, source credentials, or unbounded binary data.
- Preserve current JSON/image and GeoJSON/CSV behavior while migrating it to the shared capability contract.
- Preserve the existing ZIP download ticket endpoint and include all registered task artifacts.
- Leave `start-multisource-backend-local.cmd` untouched.

---

### Task 1: Define artifact view capabilities

**Files:**
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Create: `rsimageprocess-view/src/features/multisource/artifactCapabilities.ts`
- Test: `rsimageprocess-view/src/features/multisource/artifactCapabilities.test.ts`
- Modify: `JavaRSproject/htht-admin/.../ExecutionProgressResponse.java`
- Modify: `JavaRSproject/htht-admin/.../ProcessingExecutionController.java`
- Test: `JavaRSproject/htht-admin/.../ProcessingExecutionControllerTest.java`

- [ ] Write failing frontend and controller tests for final COG, GeoJSON, CSV, NetCDF, Parquet, JSON, PNG, and intermediate NPZ.
- [ ] Run the focused tests and observe capability functions or DTO accessors missing.
- [ ] Implement shared types and server capability mapping from artifact file names.
- [ ] Re-run focused tests.

### Task 2: Reshape task progress dialog

**Files:**
- Modify: `rsimageprocess-view/src/components/LeftPanel/TaskProgressDialog.vue`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts`

- [ ] Add failing tests for execution-detail and artifact-list sections, retention filtering, disabled View reason, per-artifact download, and successful-task ZIP action.
- [ ] Implement the two collapsible sections and dense artifact table using the capability contract.
- [ ] Connect the ZIP button to the existing `downloadProcessingResult` action.
- [ ] Run frontend tests.

### Task 3: Add bounded artifact inspection

**Files:**
- Modify: `JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/contracts.py`
- Modify: `JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Create: `JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/artifact_inspection.py`
- Test: `JavaRSproject/rs-algorithm-worker/tests/test_artifact_inspection.py`
- Modify: `JavaRSproject/htht-admin/.../ProcessingExecutionController.java`
- Test: `JavaRSproject/htht-admin/.../ProcessingExecutionControllerTest.java`
- Create: `rsimageprocess-view/src/features/multisource/artifactInspection.ts`
- Test: `rsimageprocess-view/src/features/multisource/artifactInspection.test.ts`

- [ ] Add failing tests for bounded metadata/sample responses and task ownership checks.
- [ ] Add `inspect-artifact` Worker transport operation with JSON, CSV, GeoJSON, NetCDF, Parquet, and NPZ handlers.
- [ ] Add authorized controller inspection endpoint and frontend response parser.
- [ ] Run focused Python, Maven, and frontend tests.

### Task 4: Render vector/table/dataset previews

**Files:**
- Create: `rsimageprocess-view/src/features/multisource/ArtifactInspectionDialog.vue`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/artifactInspection.test.ts`

- [ ] Add failing tests for map property tables, tabular samples, and dataset metadata.
- [ ] Render inspection modes in the existing preview surface and clear temporary map state between artifacts.
- [ ] Run frontend tests and production build.

### Task 5: Produce protected raster tiles

**Files:**
- Modify: `JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Create: `JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/artifact_tiles.py`
- Test: `JavaRSproject/rs-algorithm-worker/tests/test_artifact_tiles.py`
- Modify: `JavaRSproject/htht-admin/.../ProcessingExecutionController.java`
- Create: `JavaRSproject/htht-admin/.../ArtifactPreviewTicketService.java`
- Test: `JavaRSproject/htht-admin/.../ProcessingExecutionControllerTest.java`

- [ ] Add failing tile ticket, expiry, ownership, bounds, and PNG-response tests.
- [ ] Create a signed, short-lived ticket restricted to task and artifact checksum.
- [ ] Render only requested COG/GeoTIFF tile windows through rasterio and cache with checksum plus z/x/y.
- [ ] Return a tile visualization descriptor and load it through the existing Cesium imagery layer code.
- [ ] Run Python, Maven, frontend regression, and visual map checks.

### Task 6: Full verification

- [ ] Run `pnpm test:multisource` and `pnpm build` in `rsimageprocess-view`.
- [ ] Run `mvn -pl htht-admin test` in `JavaRSproject`.
- [ ] Run focused Worker tests for inspection and tiles.
- [ ] Run `git diff --check` in both repositories.
