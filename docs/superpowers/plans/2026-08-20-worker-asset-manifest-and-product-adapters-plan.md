# Worker Asset Manifest And Product Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Secure local Worker file access and implement real-sample inspectors for four science profiles plus the constrained GF-6 WFV browse profile.

**Architecture:** Java contracts retain logical URIs and immutable snapshots. Infrastructure maps authorized URIs to a per-request local manifest. Python validates that manifest before product selection. GF browse is a visual-only `SUPPORTED` profile and never represents scientific GF L1A support.

**Tech Stack:** Java 17, JUnit 5, Jackson, MyBatis-Plus, PostgreSQL JSONB, Python 3.11+, pytest, Pillow, h5py, netCDF4.

**Runtime prerequisite:** Create `rs-algorithm-worker/.venv` with Python 3.11+. The current system Python 3.7 and its packages are unsupported; do not alter it.

---

### Task 1: Create the Isolated Python Worker Runtime

**Files:**
- Create: `rs-algorithm-worker/pyproject.toml`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/__init__.py`
- Create: `rs-algorithm-worker/tests/test_runtime_contract.py`

- [x] **Step 1: Write the failing interpreter test**

```python
def test_runtime_requires_python_311_or_newer():
    import sys
    assert sys.version_info >= (3, 11)
```

- [x] **Step 2: Run it in the required virtual environment**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker"
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pytest tests\test_runtime_contract.py -q
```

Expected: Python 3.11 is available and test collection runs. If `py -3.11` is unavailable, stop for environment installation.

- [x] **Step 3: Add reproducible package metadata**

```toml
[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"
[project]
name = "rs-algorithm-worker"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = ["Pillow>=10,<11", "h5py>=3.10,<4", "netCDF4>=1.6,<2"]
[project.optional-dependencies]
test = ["pytest>=8,<9"]
[project.scripts]
rs-worker = "rs_algorithm_worker.cli:main"
```

- [x] **Step 4: Install and verify the runtime**

Run: `.\.venv\Scripts\python.exe -m pip install -e ".[test]"; .\.venv\Scripts\python.exe -m pytest tests\test_runtime_contract.py -q`

Expected: `1 passed`.

- [x] **Step 5: Commit the Worker scaffold**

```powershell
git add rs-algorithm-worker
git commit -m "feat: scaffold multisource python worker"
```

### Task 2: Add Candidate Metadata Snapshots and Stage File Inputs

**Files:**
- Modify: `domain/model/ProductPackage.java`, `domain/model/ProductCandidate.java`
- Modify: `application/port/out/model/StageExecutionRequest.java`
- Modify: `infrastructure/worker/WorkerContractCodec.java`, `WorkerResultTranslator.java`
- Test: `domain/model/ProductCandidateMetadataTest.java`, `infrastructure/worker/WorkerContractCodecTest.java`

- [x] **Step 1: Write failing domain and codec tests**

```java
@Test
void candidatePreservesObjectMetadataSnapshot() {
    ProductCandidate candidate = new ProductCandidate("gf6-1", new ProductProfileKey("GF-6-WFV-BROWSE", "v1"),
        ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(asset()), "{\"cloudPercent\":27}", 0.98);
    assertThat(candidate.metadataSnapshot()).isEqualTo("{\"cloudPercent\":27}");
}
```

- [x] **Step 2: Run focused Java tests and confirm the contract is absent**

Run Maven for `ProductCandidateMetadataTest,WorkerContractCodecTest` with `-DfailIfNoTests=false`.

Expected: compilation fails for the missing metadata and stage fields.

- [x] **Step 3: Implement immutable snapshots and additive Worker fields**

```java
public record ProductPackage(String packageId, ProductProfileKey profileKey,
                             ProductDataShape dataShape, List<InputAsset> files,
                             String metadataSnapshot) { }
public record StageExecutionRequest(..., String parameterSnapshot,
                                    List<InputAsset> inputs, StorageUri outputUri,
                                    ExecutionGrant executionGrant) { }
```

Copy lists defensively, require non-blank metadata JSON and non-empty stage inputs, and encode candidate `metadataSnapshot`, stage `inputs`, and `outputUri`. Require a JSON object metadata snapshot when translating inspection responses. Update all fixtures with `{}` and explicit stage inputs/output URI.

- [x] **Step 4: Re-run focused tests and commit**

Run the Step 2 command; expect no database access. Then:

```powershell
git add htht-modules\htht-multisource\src\main htht-modules\htht-multisource\src\test
git commit -m "feat: preserve worker candidate metadata"
```

### Task 3: Persist Snapshots and Publish the Constrained GF Profile

**Files:**
- Create: `script/sql/migrations/V20260820_01__add_product_metadata_snapshots.sql`
- Create: `script/sql/migrations/V20260820_02__seed_gf6_browse_profile.sql`
- Modify: `BatchProductEntity.java`, `TaskProductEntity.java`, `TaskPersistenceMapper.java`
- Test: `RepositoryAdapterTest.java`

- [x] **Step 1: Write a failing persistence round-trip test**

```java
@Test
void repositoriesRoundTripCandidateMetadataSnapshot() {
    var candidate = RepositoryFixtures.gfBrowseCandidate("{\"cloudPercent\":27}");
    assertThat(persistenceMapper.toBatchCandidate(persistenceMapper.toBatchProductEntity(11L, candidate)))
        .isEqualTo(candidate);
}
```

- [x] **Step 2: Run `RepositoryAdapterTest` and confirm missing fields**

Expected: compilation fails because product entities lack `metadataSnapshot`.

- [x] **Step 3: Add forward-only migrations and JSONB mappings**

Add `metadata_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb` with an object check to `rs_batch_product` and `rs_task_product`. Seed `GF-6-WFV-BROWSE/v1` as `RASTER_GRID/SUPPORTED`, and link only `BROWSE_VISUAL_FEATURES/v1`; its definition forbids radiometric calibration, spectral index, and orthorectification. Map with `PostgreSqlJsonbTypeHandler` and reconstruct snapshots unchanged.

- [x] **Step 4: Verify Java repository tests and disposable migrations**

Run `mvn ... -Dtest=RepositoryAdapterTest` and the existing disposable migration verification script. Expected: no writes to `public` or the development schema.

- [x] **Step 5: Commit persistence work**

```powershell
git add script\sql\migrations htht-modules\htht-multisource\src\main htht-modules\htht-multisource\src\test
git commit -m "feat: persist product metadata snapshots"
```

### Task 4: Implement the Local Authorized Asset Manifest

**Files:**
- Create: `LocalWorkerAssetResolver.java`, `WorkerAssetManifest.java`, `LocalWorkerAssetManifestWriter.java`
- Modify: `WorkerInvocation.java`, `LocalWorkerProcessLauncher.java`, `LocalProcessWorkerGateway.java`
- Test: `LocalWorkerAssetManifestWriterTest.java`, `LocalWorkerProcessLauncherTest.java`, `LocalProcessWorkerGatewayTest.java`

- [x] **Step 1: Write failing manifest security tests**

```java
@Test
void rejectsResolvedPathOutsideConfiguredAssetRoot() {
    assertThatThrownBy(() -> writer.write("inspect-01", grant(), List.of(asset("rs://input/escape"))))
        .isInstanceOf(WorkerProtocolException.class).hasMessageContaining("asset root");
}
```

Add separate tests for missing files, duplicate URI, duplicate role/ordinal, expired grant, and output-directory containment.

- [x] **Step 2: Run focused tests and confirm missing manifest types**

Expected: compilation fails because the manifest writer and manifest value are absent.

- [x] **Step 3: Implement the resolver, writer, and launcher handoff**

```java
public interface LocalWorkerAssetResolver {
    Path resolve(String grantReference, StorageUri storageUri);
}
public record WorkerAssetManifest(String requestId, String grantReference,
    Instant expiresAt, List<Input> inputs, Path outputPath) {
    public record Input(String storageUri, String role, int ordinal, Path path, String checksum) { }
}
```

Require real paths beneath the configured asset root, write only `asset-manifest.json`, create `<request>/output`, and pass `--asset-manifest`. Do not place absolute paths in request/response JSON.

- [x] **Step 4: Inject manifest creation into the gateway**

Use inspection inputs/read grant, plan candidate files/read grant, and stage inputs/output URI/execution grant. Keep the gateway repository-free.

- [x] **Step 5: Run focused Worker tests and commit**

Run codec, gateway, launcher, and manifest tests; expect zero failures. Commit as `feat: authorize local worker assets by manifest`.

### Task 5: Build the Python Worker Envelope, Manifest Validation, and CLI

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/contracts.py`, `cli.py`, `registry.py`
- Create: `rs-algorithm-worker/tests/test_contracts.py`, `test_cli.py`

- [ ] **Step 1: Write failing Python boundary tests**

```python
def test_rejects_manifest_uri_not_declared_by_request(tmp_path):
    with pytest.raises(ProtocolError, match="does not match manifest"):
        validate_invocation(inspect_request(["rs://input/a.jpg"]), manifest(["rs://input/b.jpg"], tmp_path))

def test_cli_writes_response_atomically(tmp_path):
    assert run_cli(tmp_path).returncode == 0
    assert json.loads((tmp_path / "response.json").read_text())["schemaVersion"] == "1.0"
```

Add separate tests for expiration, duplicate role/ordinal, and request ID mismatch.

- [ ] **Step 2: Run tests and confirm imports fail**

Expected: collection fails because Worker contract modules are absent.

- [ ] **Step 3: Implement strict envelope/manifest validation and CLI**

Require schema `1.0`, matching request ID, unexpired manifest, unique entries, exact request/manifest URI sets, and output containment. Convert invalid input to non-retryable `INVALID_ASSET_MANIFEST` without returning local paths. Write responses by sibling temp file plus atomic replace.

- [ ] **Step 4: Run Python boundary tests and commit**

Run `.\.venv\Scripts\python.exe -m pytest tests\test_contracts.py tests\test_cli.py -q`; expect all pass. Commit as `feat: add worker file contract cli`.

### Task 6: Implement the Four Approved Real-Sample Adapters

**Files:**
- Create: `product_adapters/base.py`, `fy3h_mersi.py`, `hy1c_czi.py`, `fy4b_agri.py`, `hy2d_altimeter.py`
- Create: `tests/conftest.py`, `test_fy3h_mersi.py`, `test_hy1c_czi.py`, `test_fy4b_agri.py`, `test_hy2d_altimeter.py`

- [ ] **Step 1: Write real-sample tests with explicit roots**

```python
@pytest.mark.real_sample
def test_fy3h_splits_five_granules(fy3h_manifest):
    candidates = inspect_manifest(fy3h_manifest)
    assert len(candidates) == 5
    assert {item["dataShape"] for item in candidates} == {"SWATH_RASTER"}

@pytest.mark.real_sample
def test_hy2d_is_along_track(hy2d_manifest):
    assert {item["dataShape"] for item in inspect_manifest(hy2d_manifest)} == {"ALONG_TRACK_OBSERVATION"}
```

Use `MULTISOURCE_FY3H_SAMPLE_ROOT`, `MULTISOURCE_HY1C_SAMPLE_ROOT`, `MULTISOURCE_FY4B_SAMPLE_FILE`, and `MULTISOURCE_HY2D_SAMPLE_ROOT`. Skip only tests whose real root is absent; never pass raw paths as product input.

- [ ] **Step 2: Run tests before implementation**

Expected: synthetic tests fail due to absent adapters; missing sample roots produce explicit skips.

- [ ] **Step 3: Implement evidence-based inspection and minimal plans**

Use HDF5/NetCDF structure and required metadata, not filenames alone. Emit one `SWATH_RASTER` candidate per FY-3H granule, HY-1C H5/XML package, `GEOSTATIONARY_RASTER` FY-4B package, and `ALONG_TRACK_OBSERVATION` HY-2D package. Reject incomplete evidence instead of guessing dataset paths; every supported plan starts with `validate-source`.

- [ ] **Step 4: Run all four suites with sample variables configured**

Expected: inspection and minimal-plan tests pass and adapters read only manifest-listed files.

- [ ] **Step 5: Commit approved adapters**

```powershell
git add rs-algorithm-worker
git commit -m "feat: inspect first multisource product samples"
```

### Task 7: Implement Constrained GF-6 WFV Browse Processing

**Files:**
- Create: `product_adapters/gf6_wfv_browse.py`
- Create: `tests/test_gf6_wfv_browse.py`, `tests/fixtures/gf6_invalid/`

- [ ] **Step 1: Write failing GF tests**

```python
@pytest.mark.real_sample
def test_gf_directory_yields_two_browse_candidates(gf_manifest):
    candidates = inspect_manifest(gf_manifest)
    assert [item["packageId"] for item in candidates] == ["GF6-WFV-LEVEL1A-1420733151", "GF6-WFV-LEVEL1A-1420734648"]
    assert {item["dataShape"] for item in candidates} == {"RASTER_GRID"}
    assert candidates[0]["metadataSnapshot"]["georeferencingMethod"] == "AFFINE_WORLD_FILE_APPROXIMATE"

def test_scientific_operation_is_rejected(gf_candidate):
    with pytest.raises(WorkerFailure, match="UNSUPPORTED_BROWSE_OPERATION"):
        build_plan(gf_candidate, {"operation": "RADIOMETRIC_CALIBRATION"})
```

Synthetic tests also cover missing JGW, non-finite coefficients, and invalid JSON.

- [ ] **Step 2: Run the GF test before implementation**

Run with `$env:MULTISOURCE_GF_SAMPLE_ROOT='C:\Users\24257\Desktop\GIS开发\GF'`; expected: adapter registration failure before implementation.

- [ ] **Step 3: Implement strict grouping and inspection**

Require exactly `BROWSE_IMAGE`, `GEOREFERENCE`, and `METADATA` files with one common stem. Parse positive RGB JPEG dimensions; six finite JGW values with non-zero determinant; JSON `GF6`, `WFV`, `LEVEL1A`, matching ID, ordered acquisition interval, cloud `[0,100]`, and a closed longitude/latitude polygon. Compare JGW origin with the footprint tolerance. Return `SUPPORTED` only for `BROWSE_VISUAL_FEATURES`.

- [ ] **Step 4: Implement the visual-only plan**

Return `validate-browse -> georeference-browse -> extract-visual-features -> write-browse-report`. Write deterministic report JSON below manifest output. Reject calibration, spectral indices, atmospheric correction, RPC/orthorectification, terrain correction, and quality-mask interpretation with non-retryable `UNSUPPORTED_BROWSE_OPERATION`.

- [ ] **Step 5: Run synthetic and real tests and commit**

Expected: two real candidates and no scientific plan. Commit as `feat: add constrained GF6 browse adapter`.

### Task 8: Cross-Language Verification and Documentation Sync

**Files:**
- Modify: `docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md`
- Test: opt-in Java/Python integration test under `infrastructure/worker`

- [ ] **Step 1: Add an opt-in integration test**

Tag it `integration` and skip unless `MULTISOURCE_GF_SAMPLE_ROOT` and `MULTISOURCE_WORKER_PYTHON` are set. Assert Java receives two `GF-6-WFV-BROWSE` candidates through the authorized manifest; do not connect to PostgreSQL.

- [ ] **Step 2: Run Java and Python regressions**

```powershell
& 'C:\Users\24257\Desktop\GIS开发\.tools\apache-maven-3.9.16\bin\mvn.cmd' '-Dmaven.repo.local=C:/Users/24257/.m2/repository' -pl htht-modules\htht-multisource -am test
cd rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest -q
```

Expected: all unit tests pass; real integration tests run only with explicit variables.

- [ ] **Step 3: Review and commit**

Confirm Worker responses contain no absolute paths, domain code has no filesystem/Python imports, Gateway has no repository dependency, and the broad plan describes GF as visual-only. Mark only verified steps complete, then commit the adapter slice.
