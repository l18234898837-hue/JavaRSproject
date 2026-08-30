# FY4B AGRI and GF6 WFV L1A Single-Stage Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add production-facing FY-4B AGRI L1 and GF-6 WFV L1A product modules to the existing multisource backend and frontend without changing HY-1C, HY-2D, or FY-3H behavior.

**Architecture:** Each new product uses a strict product adapter plus one Java-visible integrated Worker stage. FY-4B executes the source repository's validation, calibration, nominal navigation, reprojection, COG, and optional algorithms internally; GF-6 inspects once, creates one RPC-resampled standard COG, and reuses it for optional outputs. The existing REST/task lifecycle and frontend workspace remain shared.

**Tech Stack:** Java 17/Spring/Maven, Python 3.11/pytest/h5py/rasterio/numpy/scipy, Vue 3/TypeScript/Element Plus/tsx tests/pnpm.

**Spec:** `docs/superpowers/specs/2026-08-28-fy4b-gf6-single-stage-integration-design.md`

## Global Constraints

- Preserve every pre-existing uncommitted change in `F:\python_program\rsimageprocess-view\Backend\JavaRSproject` and `F:\python_program\rsimageprocess-view\rsimageprocess-view`.
- Do not change the existing HY-1C `HY-1C-CZI/L2A-v1`, HY-2D `HY-2D-ALTIMETER/v1`, or FY-3H `FY-3H-MERSI/L1-1000M-GEO1K-v1` profile keys, integrated stage codes, input roles, output artifact types, or relative paths.
- New Java-visible stages are exactly `STANDARDIZE_FY4B_AGRI` and `STANDARDIZE_GF6_WFV_L1A`.
- FY-4B profile is exactly `FY-4B-AGRI/v1`; GF-6 engineering profile is exactly `GF-6-WFV-L1A/v1`.
- Worker responses expose only logical `rs://output/...` URIs, byte counts, and `sha256:` checksums; no local path may appear.
- GF-6 navigation is `NOMINAL_RPC_NO_DEM` with `rpcHeightMode=CONSTANT`, `rpcHeight=0`, and `accuracyStatus=UNVALIDATED`.
- FY-4B navigation remains `NOMINAL_UNVERIFIED`; fusion mode is `NIR_RED_BLUE` and is not labelled true color.
- Use tests first and observe the expected RED failure before production code for each behavior.
- Do not run the complete real FY-4B full-disk or 14GB GF-6 processing chain; real samples are inspection-only smoke inputs.
- Do not commit because the required baseline exists as user-owned uncommitted changes on `main`; verification uses file diffs and fresh commands.
- The requested Maven settings path `F:\python_program\rsimageprocess-view\Backend\maven-settings.xml` is absent. Use `D:\maven\maven\apache-maven-3.8.8\bin\mvn.cmd` with `-Dmaven.repo.local=F:\python_program\rsimageprocess-view\Backend\.m2\repository` and Maven's default settings.

---

### Task 1: FY-4B strict evidence, parameters, and integrated plan

**Files:**

- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_profile.py`
- Create: `rs-algorithm-worker/tests/fy4b_fixture.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri.py`

**Interfaces:**

- Produces: `inspect_manifest(manifest) -> list[dict]`, `build_integrated_plan(product, parameter_snapshot) -> dict`, `parse_fy4b_parameters(raw) -> dict`.
- Candidate files are ordered roles `L1_0500M`, `L1_1000M`, `L1_2000M`, `L1_4000M`, `GEO_4000M`.
- The plan contains one stage with code/key `STANDARDIZE_FY4B_AGRI`, all five raw inputs, normalized JSON parameter snapshot, and exact enabled final outputs.

- [ ] **Step 1: Write failing evidence tests.** Add tests that create a five-file HDF fixture, assert profile `FY-4B-AGRI/v1`, assert the exact role order, reject a missing resolution, reject mixed observation times, and reject an FDI file placed in the GEO role.

```python
candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]
assert candidate["profileKey"] == {
    "productCode": "FY-4B-AGRI",
    "profileVersion": "v1",
}
assert [item["role"] for item in candidate["files"]] == [
    "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
]
```

- [ ] **Step 2: Run the tests and observe RED.**

```powershell
& F:\python_program\rsimageprocess-view\Backend\.venv-rs-worker\Scripts\python.exe -m pytest -q tests/test_fy4b_agri.py
```

Expected: failures because the current adapter accepts one generic HDF and has no strict five-file package.

- [ ] **Step 3: Port the strict adapter and parameter parser.** Migrate the source repository's filename/HDF checks and normalize this default parameter object:

```python
{
    "schemaVersion": "1",
    "targetCrs": "EPSG:4326",
    "roi": None,
    "features": {"enabled": False, "metrics": ["statistics"]},
    "fusion": {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]},
    "saliency": {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
}
```

- [ ] **Step 4: Write and observe RED for the integrated plan.** Assert a single stage, the exact five raw types, and optional output presence/absence.

```python
plan = build_integrated_plan(candidate, {"schemaVersion": "1", "features": {"enabled": False, "metrics": ["statistics"]}})
assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_FY4B_AGRI"]
assert "FY4B_RED_500M_COG" in {item["artifactType"] for item in plan["stages"][0]["outputs"]}
assert "FY4B_FEATURE_COG" not in {item["artifactType"] for item in plan["stages"][0]["outputs"]}
```

- [ ] **Step 5: Implement the minimal integrated plan and run GREEN.** Preserve the source adapter's public multi-stage `build_plan` only if existing source tests need it; registry integration must use `build_integrated_plan`.

### Task 2: FY-4B integrated execution

**Files:**

- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_navigation.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`
- Create: `rs-algorithm-worker/tests/test_fy4b_agri_navigation.py`
- Create: `rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py`

**Interfaces:**

- Produces: `execute_fy4b_product(payload, manifest) -> Worker response`.
- Internally preserves source phase order but returns only the final artifact set declared by Task 1.
- Hashing is streaming and never uses `Path.read_bytes()` on large rasters.

- [ ] **Step 1: Write failing pure tests for calibration, quality masks, nominal grid, and streaming windows.** Use 3×3 arrays with literal expected results for fill, out-of-range, lookup-table calibration, thermal calibration, CRS/status, and window boundaries.

- [ ] **Step 2: Run those tests and observe missing-module RED.**

```powershell
& F:\python_program\rsimageprocess-view\Backend\.venv-rs-worker\Scripts\python.exe -m pytest -q tests/test_fy4b_agri_navigation.py tests/test_fy4b_agri_pipeline.py
```

- [ ] **Step 3: Port source scientific primitives and the stage executor.** Keep science resampling bilinear, quality resampling nearest, and quality bits `1,2,4,8,16,32` for fill, range, navigation, calibration, file quality, and outside disk.

- [ ] **Step 4: Write a failing integrated orchestration test.** Monkeypatch each heavy phase, assert the order below, assert intermediate artifacts are not returned, and assert disabled optional phases are not invoked.

```python
assert calls == [
    "VALIDATE_FY4B_PACKAGE",
    "BUILD_FY4B_NOMINAL_GRID",
    "CALIBRATE_AND_GEOREFERENCE_FY4B",
    "REPROJECT_AND_CROP_FY4B",
    "WRITE_FY4B_STANDARD_COG",
]
assert all("native/" not in item["uri"] and "grid/" not in item["uri"] for item in response["artifacts"])
```

- [ ] **Step 5: Implement `execute_fy4b_product`.** Construct internal phase manifests from the current invocation directory, feed prior phase artifact paths to the next phase, gather standard/optional finals, remove only its `validation`, `navigation`, `native`, `calibration`, and `grid` intermediate directories, and stream size/SHA-256 calculation.

- [ ] **Step 6: Run FY-4B tests GREEN.** Also set `MULTISOURCE_FY4B_SAMPLE_ROOT` and run the real-package inspection test only.

### Task 3: GF-6 WFV L1A evidence, integrated execution, and profile seed

**Files:**

- Create: `rs-algorithm-worker/src/rs_algorithm_worker/models_gf6.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_mosaic.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_rpc.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/gf6_processing.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`
- Create: `rs-algorithm-worker/tests/gf6_fixture.py`
- Create: `rs-algorithm-worker/tests/test_gf6_processing.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_l1a.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_pipeline.py`
- Create: `script/sql/migrations/V20260828_01__seed_gf6_wfv_l1a_profile.sql`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/MigrationScriptTest.java`

**Interfaces:**

- Produces: `inspect_manifest`, `build_integrated_plan`, `parse_parameters`, and `execute_gf6_product`.
- Required roles: `MAIN_TIFF_TILE` ordinals 0–2, `METADATA_XML`, `RPC_RPB`, `NAV`, `TILE_INDEX_TIL`.
- Optional roles: `FOOTPRINT_SHP`, `FOOTPRINT_DBF`, `FOOTPRINT_SHX`, all-or-none.
- The integrated stage writes `GF6_STANDARD_COG`, `GF6_PROCESSING_SUMMARY_JSON`, and enabled optional outputs.

- [ ] **Step 1: Write failing evidence/parameter tests.** Assert exactly three ordered 8-band UInt16 TIFFs, main RPB selection, XML identity `GF6/WFV/LEVEL1A`, TIL order, normalized shared basename, optional footprint all-or-none, target CRS, positive resolution, constant 0m height, ROI, band, feature, RGB, and saliency validation.

- [ ] **Step 2: Observe RED, then port and harden the source adapter/models/operators.** Use TIL coverage for the processing canvas and record both `declaredDimensions` and `tileCoverageDimensions` plus their delta in metadata.

- [ ] **Step 3: Write a failing integrated pipeline test.** Monkeypatch `_write_standard_cog`; assert it is called exactly once when standard, features, fusion, and saliency are all enabled. Assert each optional writer consumes the same standard path.

- [ ] **Step 4: Implement the one-pass pipeline.** Reuse the generated standard COG rather than `_standard_source` regeneration. Keep temporary VRT removal in `finally`, windowed 256×256 RPC resampling, `AGain*DN+AOffset`, and streaming SHA-256.

- [ ] **Step 5: Write and implement the SQL seed.**

```sql
INSERT INTO rs_product_profile(product_code, profile_version, display_name, data_shape, support_status)
VALUES ('GF-6-WFV-L1A', 'v1', 'GF-6 WFV L1A', 'RASTER_GRID', 'SUPPORTED')
ON CONFLICT (product_code, profile_version) DO UPDATE
SET display_name = EXCLUDED.display_name,
    data_shape = EXCLUDED.data_shape,
    support_status = EXCLUDED.support_status;
```

- [ ] **Step 6: Run GF-6 Python tests and the migration test GREEN.** Real sample smoke may open metadata and TIFF headers only; it must not write a full output.

### Task 4: Shared Worker routing and upload limits

**Files:**

- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/tests/test_registry.py`
- Modify: `rs-algorithm-worker/tests/test_cli.py`
- Modify: `htht-admin/src/main/resources/application.yml`
- Modify: `script/docker/nginx/conf/nginx.conf`

**Interfaces:**

- Consumes Task 1–3 adapters and integrated executors.
- Existing routes and executors remain byte-for-byte behaviorally compatible.

- [ ] **Step 1: Write failing registry tests.** Inspect/build/execute one FY-4B synthetic package and one GF-6 synthetic package; assert exact profile/stage and logical artifact response. Re-run current HY-1C/HY-2D/FY-3H registry tests in the same RED command to prove they remain present.

- [ ] **Step 2: Implement specific adapter selection before fallback.** Detect GF-6 L1A from TIFF+TIL+RPB evidence, FY-4B from the five filename roles, and retain the current GF-6 Browse, `.tar.gz`, HY-1C, and FY-3H order. Route only the two exact new profile codes to new executors.

- [ ] **Step 3: Preserve actionable validation errors in CLI tests.** The response error message for a `ValueError("GF6_PARAMETER_INVALID: roi")` or FY-4B validation failure must contain that actionable text and must not contain a traceback or local path.

- [ ] **Step 4: Raise upload ceilings.** Set Spring `max-file-size: 10000MB`, retain `max-request-size: 10000MB` because files upload one at a time, and set nginx `client_max_body_size 10g`.

- [ ] **Step 5: Run Worker regression GREEN.**

```powershell
& F:\python_program\rsimageprocess-view\Backend\.venv-rs-worker\Scripts\python.exe -m pytest -q
```

### Task 5: Frontend product contracts, file validation, and parameters

**Files:**

- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\productTemplates.ts`
- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\productTemplates.test.ts`
- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\useProcessingTask.ts`
- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\processingTaskRules.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\fy4bAgriFiles.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\fy4bAgriFiles.test.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\fy4bAgriParameters.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\fy4bAgriParameters.test.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\gf6WfvL1aFiles.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\gf6WfvL1aFiles.test.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\gf6WfvL1aParameters.ts`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\gf6WfvL1aParameters.test.ts`

**Interfaces:**

- Produces templates `FY4B_AGRI_TEMPLATE` and `GF6_WFV_L1A_TEMPLATE`.
- Replaces only `GF6_WFV_BROWSE_TEMPLATE` in `PRODUCT_TEMPLATES`; backend Browse fallback remains untouched.
- Adds slot IDs for all declared roles and serializes exact Worker parameter schema.

- [ ] **Step 1: Write RED file-pairing tests.** FY-4B must reject mixed time/version/resolution/kind. GF-6 must normalize tile suffixes, reject duplicate/missing tile ordinals, select the main RPB basename, and require SHP/DBF/SHX all-or-none.

- [ ] **Step 2: Implement pure file validators and run GREEN.** Expected values are literal sample filenames from the provided datasets.

- [ ] **Step 3: Write RED parameter tests.** Assert exact default snapshots and invalid ROI/window/percentile/band/source cases. GF-6 snapshot always sends `rpcHeightMode: "CONSTANT"` and `rpcHeight: 0`.

- [ ] **Step 4: Implement parameter models and run GREEN.** Keep UI-only labels out of snapshots.

- [ ] **Step 5: Add templates and generalized slot state.** Candidate selection must require exact product code/profile version for both new products; remove the frontend-only legacy package fallback for the replaced GF-6 template without altering backend compatibility.

### Task 6: Frontend drawers and workspace integration

**Files:**

- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\components\LeftPanel\MultiSourceSatelliteProcess.vue`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\Fy4bAgriParameterDrawer.vue`
- Create: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\Gf6WfvL1aParameterDrawer.vue`
- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\productParameterDrawer.less`
- Modify: `F:\python_program\rsimageprocess-view\rsimageprocess-view\src\features\multisource\productParameterDrawer.test.ts`

**Interfaces:**

- Consumes Task 5 forms, validation, summaries, snapshots, and templates.
- Uses the existing mission-control visual tokens and interaction structure.

- [ ] **Step 1: Write a failing structural test.** Assert both drawers reuse `productParameterDrawer.less`, have an accessible title, expose only supported fields, and that the workspace contains both product branches and stage labels.

- [ ] **Step 2: Add FY-4B and GF-6 parameter summaries/drawers.** FY-4B displays CRS/ROI/features/false-color/saliency and the `NOMINAL_UNVERIFIED` warning. GF-6 displays resolution/ROI/bands/features/RGB/saliency and all three engineering limitations.

- [ ] **Step 3: Integrate snapshot selection and stage labels.** Add labels exactly:

```text
STANDARDIZE_FY4B_AGRI -> 处理 FY-4B AGRI 全圆盘产品
STANDARDIZE_GF6_WFV_L1A -> 处理 GF-6 WFV L1A 工程产品
```

- [ ] **Step 4: Run frontend GREEN and fresh build checks.**

```powershell
pnpm test:multisource
pnpm typecheck
pnpm build
```

### Task 7: Integrated verification and regression review

**Files:**

- Verify all files changed by Tasks 1–6; do not add unrelated refactors.

- [ ] **Step 1: Run Worker focused and full suites.** Report pass/skip/fail counts separately.

- [ ] **Step 2: Run Java tests using the fixed Maven command.**

```powershell
& D:\maven\maven\apache-maven-3.8.8\bin\mvn.cmd '-Dmaven.repo.local=F:\python_program\rsimageprocess-view\Backend\.m2\repository' -pl htht-modules/htht-multisource,htht-admin -am '-DskipTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- [ ] **Step 3: Run frontend tests, typecheck, and build.**

- [ ] **Step 4: Run `git diff --check` in both repositories and inspect status.** Confirm no existing user file was reverted and no runtime sample output entered either repository.

- [ ] **Step 5: Request a read-only final code review against the spec.** Fix Critical and Important findings, then re-run only the affected focused tests plus all three final verification commands.
