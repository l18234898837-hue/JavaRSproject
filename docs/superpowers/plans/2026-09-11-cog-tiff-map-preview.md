# COG/TIFF Map Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow eligible final and intermediate COG/GeoTIFF artifacts to be loaded as authenticated PNG tile layers in the existing Cesium map, while retaining direct downloads for every artifact.

**Architecture:** The Python Worker is the only component that opens raster files. It validates a registered artifact's spatial metadata and renders only requested Web Mercator tile windows. Spring Boot authorizes each map session with a short-lived, multi-request artifact ticket, invokes the Worker through the existing manifest boundary, caches PNG tiles by immutable checksum, and returns an opaque tile URL plus WGS84 bounds. The Vue client requests this descriptor only after the operator presses View, then uses the existing Cesium imagery-layer controls.

**Tech Stack:** Vue 3, TypeScript, Cesium, Spring Boot 2.7 / Java 17, Python 3.11, rasterio/GDAL, pytest, JUnit 5, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-11-multisource-artifact-experience-design.md`

## Global Constraints

- Use only the existing `rs-algorithm-worker` runtime; it already declares `rasterio>=1.4,<2` and `pyproj>=3.6,<4`.
- Do not expose absolute filesystems paths, Worker manifests, bearer tokens, or storage keys in any API payload, log, or tile URL.
- A tile ticket is scoped to exactly one task, artifact type, checksum, and authenticated owner; it expires after two minutes and is reusable only within that interval for multiple tile requests.
- Render at most one 256 x 256 PNG tile per Worker call. Reject invalid zoom, row, and column values before Worker dispatch.
- Support only EPSG:4326 and EPSG:3857 raster CRS in the first release. For any other valid CRS, disable map view with an explicit reason and retain download.
- Use existing `ArtifactInspectionService`, `CompositeLocalWorkerAssetResolver`, and `LocalProcessWorkerGateway`; do not use the obsolete generic grid tile controller.
- Keep the current JSON/PNG, GeoJSON/CSV, individual-download, and ZIP-download behavior unchanged.
- The worktree is shared and dirty. Do not create commits during implementation unless the owner explicitly requests a commit.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `rs-algorithm-worker/src/rs_algorithm_worker/artifact_tiles.py` | Validate TIFF spatial metadata and render one bounded PNG tile without leaking the path. |
| `rs-algorithm-worker/src/rs_algorithm_worker/registry.py` | Route Worker `inspect-raster-artifact` and `render-artifact-tile` operations through the authorized one-file manifest. |
| `rs-algorithm-worker/src/rs_algorithm_worker/contracts.py` | Validate the exact request payload shape, numeric ranges, and one-artifact manifest restriction. |
| `rs-algorithm-worker/tests/test_artifact_tiles.py` | Raster metadata, CRS, COG, tile rendering, palette, and malformed-request regression tests. |
| `htht-admin/.../ArtifactPreviewTicketService.java` | Issue and validate expiring, owner-bound, artifact-bound, reusable tile tickets. |
| `htht-admin/.../ArtifactRasterPreviewService.java` | Resolve a registered artifact, call the Worker, cache metadata/tiles by checksum, and create a client-safe descriptor. |
| `htht-admin/.../ProcessingExecutionController.java` | Expose authenticated descriptor issuance and ticket-authorized PNG tile routes. |
| `htht-admin/.../dto/ArtifactTilePreviewResponse.java` | Public descriptor DTO containing URL template, WGS84 bounds, CRS, zooms, and legend only. |
| `htht-admin/.../ProcessingExecutionControllerTest.java` | Ownership, ticket expiry, invalid coordinate, response headers, descriptor, and no-secret tests. |
| `rsimageprocess-view/src/api/multiSourceSatellite.ts` | Add the descriptor request API and tile descriptor TypeScript type. |
| `rsimageprocess-view/src/features/multisource/artifactMapPreview.ts` | Validate the server descriptor before Cesium receives it. |
| `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue` | Request a descriptor on View, load it as a tile layer, and show a precise failure message. |
| `rsimageprocess-view/src/features/multisource/artifactMapPreview.test.ts` | Validate URL, bounds, CRS, zoom, legend, and disabled-view behavior. |

## Task 1: Define the Raster Eligibility Contract

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/artifact_tiles.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/contracts.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Test: `rs-algorithm-worker/tests/test_artifact_tiles.py`

**Consumes:** one authorized artifact path from the existing Worker manifest.

**Produces:** `inspect_raster_artifact(path, artifact_format) -> RasterMetadata`, serialized as:

```json
{
  "kind": "raster",
  "renderable": true,
  "reason": null,
  "crs": "EPSG:4326",
  "bounds": [110.0, 30.0, 111.0, 31.0],
  "minZoom": 0,
  "maxZoom": 16,
  "style": {"kind": "rgb"},
  "legend": null
}
```

- [ ] **Step 1: Write failing Worker tests for valid and rejected raster metadata.**

```python
def test_inspect_raster_returns_wgs84_bounds_for_a_georeferenced_cog(tmp_path):
    source = write_cog(tmp_path / "rgb.tif", crs="EPSG:4326", count=3)
    result = inspect_raster_artifact(source, "COG")
    assert result["renderable"] is True
    assert result["bounds"] == [110.0, 30.0, 111.0, 31.0]
    assert result["style"] == {"kind": "rgb"}

def test_inspect_raster_rejects_missing_crs_and_non_web_crs(tmp_path):
    assert inspect_raster_artifact(write_tiff(tmp_path / "none.tif"), "GEOTIFF")["reason"] == "TIFF 未包含可用坐标系"
    assert inspect_raster_artifact(write_cog(tmp_path / "utm.tif", crs="EPSG:32650"), "COG")["reason"] == "TIFF 坐标系暂不支持地图查看"
```

- [ ] **Step 2: Run the focused test to verify it fails.**

Run: `python -m pytest -q tests/test_artifact_tiles.py`

Expected: FAIL because `artifact_tiles` and `inspect_raster_artifact` do not exist.

- [ ] **Step 3: Implement bounded metadata inspection.**

```python
@dataclass(frozen=True)
class RasterMetadata:
    renderable: bool
    reason: str | None
    crs: str | None
    bounds: list[float] | None
    min_zoom: int | None
    max_zoom: int | None
    style: dict[str, object] | None
    legend: dict[str, object] | None

def inspect_raster_artifact(path: Path, artifact_format: str) -> dict[str, object]:
    with rasterio.open(path) as dataset:
        if dataset.crs is None:
            return _disabled("TIFF 未包含可用坐标系")
        crs = dataset.crs.to_string().upper()
        if crs not in {"EPSG:4326", "EPSG:3857"}:
            return _disabled("TIFF 坐标系暂不支持地图查看")
        if dataset.bounds.left >= dataset.bounds.right or dataset.bounds.bottom >= dataset.bounds.top:
            return _disabled("TIFF 地理范围无效")
        return _renderable(dataset, crs)
```

`_renderable` must transform bounds to EPSG:4326 with `rasterio.warp.transform_bounds(..., densify_pts=21)`, reject non-finite/world-invalid values, and derive styles as follows:

- three or more bands: `{"kind": "rgb", "bands": [1, 2, 3]}`;
- one band whose artifact type contains `MASK`: categorical palette `{"kind": "mask", "nodataAlpha": 0}` and a two-class legend;
- one band whose artifact type contains `SCORE` or `SALIENCY`: continuous palette `{"kind": "continuous", "palette": "viridis"}`;
- all other one-band TIFF: `{"kind": "grayscale"}`.

The operation payload must include only `format` and `artifactType`; `contracts.py` must reject unknown keys, a non-string format/type, more than one manifest input, or an input outside the grant.

- [ ] **Step 4: Run Worker tests and inspect the five representative artifacts.**

Run: `python -m pytest -q tests/test_artifact_tiles.py tests/test_artifact_inspection.py`

Then run the Worker metadata operation for `czi-features.tif`, `czi-rgb.tif`, `czi-l2a.tif`, `mask.tif`, and `score.tif` from a generated one-file manifest. Record each artifact's `renderable`, CRS, bounds, and style; do not log its local path.

Expected: Every response is bounded JSON. Files without a usable CRS/range return a concrete `reason`, never a stack trace.

- [ ] **Step 5: Review checkpoint.**

Confirm that the worker opens no path except `manifest.inputs[0].path`, and that the result contains no path, filename, checksum, credential, or unbounded pixel data.

## Task 2: Render Correct PNG Tiles for RGB, Mask, and Score TIFFs

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/artifact_tiles.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Test: `rs-algorithm-worker/tests/test_artifact_tiles.py`

**Consumes:** a `RasterMetadata`-eligible artifact and `{z, x, y, style}` request.

**Produces:** `render_raster_tile(...) -> bytes`, a valid 256 x 256 PNG, encoded as URL-safe Base64 in the Worker response.

- [ ] **Step 1: Write failing tile tests.**

```python
def test_render_tile_returns_a_256_png_for_rgb_cog(tmp_path):
    tile = render_raster_tile(write_cog(tmp_path / "rgb.tif"), z=8, x=206, y=105, style={"kind": "rgb", "bands": [1, 2, 3]})
    image = Image.open(io.BytesIO(tile))
    assert image.size == (256, 256)
    assert image.mode == "RGBA"

def test_render_tile_preserves_transparent_nodata_for_mask(tmp_path):
    tile = render_raster_tile(write_mask(tmp_path / "mask.tif"), z=8, x=206, y=105, style={"kind": "mask", "nodataAlpha": 0})
    assert Image.open(io.BytesIO(tile)).getchannel("A").getextrema()[0] == 0
```

- [ ] **Step 2: Run the focused test to verify it fails.**

Run: `python -m pytest -q tests/test_artifact_tiles.py -k render`

Expected: FAIL because `render_raster_tile` is not implemented.

- [ ] **Step 3: Implement tile-window rendering and limits.**

```python
def render_raster_tile(path: Path, *, z: int, x: int, y: int, style: dict[str, object]) -> bytes:
    _require_web_tile(z, x, y)
    with rasterio.open(path) as source:
        destination = np.zeros((4, 256, 256), dtype=np.uint8)
        reproject(
            source=rasterio.band(source, requested_band),
            destination=destination_band,
            src_transform=source.transform,
            src_crs=source.crs,
            dst_transform=_web_mercator_tile_transform(z, x, y),
            dst_crs="EPSG:3857",
            dst_nodata=0,
            resampling=Resampling.bilinear,
        )
        return _encode_png(_style_to_rgba(destination, style))
```

Use `Resampling.nearest` for masks, `bilinear` for RGB and score layers, dataset masks/nodata to set alpha to zero, and bounded percentile stretching based only on the requested tile's valid samples. Reject `z < 0`, `z > 22`, `x/y < 0`, or `x/y >= 2**z`; return a transparent PNG when the tile does not intersect the artifact rather than an error. Reject encoded responses larger than 512 KiB.

Route the Worker operation as `render-artifact-tile`. Its response must be:

```json
{"contentType":"image/png","pngBase64":"..."}
```

- [ ] **Step 4: Run Worker and product regression tests.**

Run: `python -m pytest -q tests/test_artifact_tiles.py tests/test_windowed_cog.py tests/test_hy1c_czi_l2a.py`

Expected: PNG dimensions, no-data transparency, invalid-coordinate rejection, and existing COG production tests all pass.

- [ ] **Step 5: Review checkpoint.**

Open generated tiles in the test only. Confirm the Worker never returns a TIFF, whole raster array, source path, or cache key.

## Task 3: Add Owner-Bound Artifact Preview Tickets and Metadata/Tile Caches

**Files:**
- Create: `htht-admin/src/main/java/com/htht/config/multisource/ArtifactPreviewTicketService.java`
- Create: `htht-admin/src/main/java/com/htht/config/multisource/ArtifactRasterPreviewService.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-admin/src/test/java/com/htht/config/multisource/ArtifactPreviewTicketServiceTest.java`
- Test: `htht-admin/src/test/java/com/htht/config/multisource/ArtifactRasterPreviewServiceTest.java`

**Consumes:** `TaskDetailView`, `AccessSubject`, a registered `TaskArtifactView`, and the existing `ArtifactInspectionService`/Worker gateway.

**Produces:** a `TileDescriptor` and PNG bytes, only after ticket validation.

- [ ] **Step 1: Write failing ticket tests.**

```java
@Test
void validatesAReusableTicketOnlyForItsExactOwnerTaskTypeAndChecksum() {
    var issued = service.issue(101L, OWNER, "STANDARD_COG", "sha256:abc");
    assertThat(service.validate(101L, "STANDARD_COG", "sha256:abc", issued.token()))
        .isEqualTo(OWNER);
    assertThatThrownBy(() -> service.validate(101L, "MASK_COG", "sha256:abc", issued.token()))
        .isInstanceOf(SecurityException.class);
}

@Test
void rejectsExpiredTicketsWithoutRemovingValidTicketsForOtherArtifacts() {
    // Advance the injected clock beyond the two-minute TTL and assert SecurityException.
}
```

- [ ] **Step 2: Run the ticket tests to verify they fail.**

Run: `mvn -pl htht-admin -Dtest=ArtifactPreviewTicketServiceTest test`

Expected: FAIL because the ticket service does not exist.

- [ ] **Step 3: Implement non-consumable short-lived ticket validation.**

```java
public IssuedTicket issue(long taskId, AccessSubject subject, String type, String checksum) { ... }
public AccessSubject validate(long taskId, String type, String checksum, String token) { ... }
```

Use the same injected `Clock`, expiry pruning, cryptographically unpredictable UUID token supplier, and `ConcurrentHashMap` pattern as `ResultDownloadTicketService`. Unlike ZIP tickets, `validate` must not remove a valid ticket because one map layer needs many z/x/y requests. Store the exact checksum so a retried/replaced artifact invalidates old tiles automatically.

- [ ] **Step 4: Write failing cache tests and implement `ArtifactRasterPreviewService`.**

```java
@Test
void reuses_metadata_and_png_cache_only_when_checksum_and_style_match() {
    var first = service.describe(detail, OWNER, artifact);
    var second = service.describe(detail, OWNER, artifact);
    assertThat(second).isEqualTo(first);
    verify(workerGateway, times(1)).inspectArtifact(any());
}
```

Cache metadata by `{taskId, type, checksum}` for five minutes and PNG bytes by `{checksum, styleFingerprint, z, x, y}` for two minutes, with a bounded maximum of 512 entries. Return `Cache-Control: private, max-age=60`; do not use a shared public cache. A non-renderable Worker result must become a stable `ArtifactPreviewUnavailableException` containing only the user-safe reason.

- [ ] **Step 5: Run Java service tests.**

Run: `mvn -pl htht-admin -Dtest=ArtifactPreviewTicketServiceTest,ArtifactRasterPreviewServiceTest test`

Expected: Ownership, expiry, cache separation, no-worker-repeat on a metadata hit, and safe errors pass.

## Task 4: Expose Safe Descriptor and Tile Endpoints

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ArtifactTilePreviewResponse.java`
- Modify: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- Modify: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`

**Consumes:** authenticated descriptor request or an owner-bound tile ticket.

**Produces:** a client-safe descriptor and `image/png` response.

- [ ] **Step 1: Write failing MockMvc tests.**

```java
mvc.perform(post("/api/multisource/processing-tasks/101/artifacts/STANDARD_COG/tile-preview"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.tileUrl").value(containsString("{z}/{x}/{y}.png?ticket=")))
    .andExpect(jsonPath("$.data.bounds").isArray())
    .andExpect(jsonPath("$.data.crs").value("EPSG:4326"));

mvc.perform(get("/api/multisource/processing-tasks/101/artifacts/STANDARD_COG/tiles/8/206/105.png")
        .param("ticket", issued.token()))
    .andExpect(status().isOk())
    .andExpect(content().contentType("image/png"))
    .andExpect(header().string("Cache-Control", "private, max-age=60"));
```

Add cases for another user, another artifact type, expired ticket, malformed token, `z=23`, `x=999` at `z=1`, non-renderable TIFF, and responses containing neither `storageKey` nor a Windows/POSIX path.

- [ ] **Step 2: Run the controller test to verify it fails.**

Run: `mvn -pl htht-admin -Dtest=ProcessingExecutionControllerTest test`

Expected: FAIL because `tile-preview` and `tiles` routes do not exist.

- [ ] **Step 3: Implement the exact endpoint contract.**

```text
POST /api/multisource/processing-tasks/{taskId}/artifacts/{type}/tile-preview
GET  /api/multisource/processing-tasks/{taskId}/artifacts/{type}/tiles/{z}/{x}/{y}.png?ticket={ticket}
```

`POST` uses the existing authenticated `currentSubject`, `detailUseCase.get`, and exact artifact-type lookup. It calls `ArtifactRasterPreviewService.describe`, issues a ticket, and returns:

```json
{
  "tileUrl": "/api/multisource/processing-tasks/101/artifacts/STANDARD_COG/tiles/{z}/{x}/{y}.png?ticket=opaque",
  "bounds": [110.0, 30.0, 111.0, 31.0],
  "crs": "EPSG:4326",
  "minZoom": 0,
  "maxZoom": 16,
  "legend": {"kind":"continuous","palette":"viridis"}
}
```

The tile route is `@SaIgnore` only because the ticket is its authorization. It validates the ticket before resolving the task detail with the ticket's stored subject, then streams Worker-provided PNG bytes with `Content-Type: image/png`, `X-Content-Type-Options: nosniff`, and the private cache header. Do not add this URL to polling progress responses; polling must not mint tickets.

- [ ] **Step 4: Re-run controller and archive-download tests.**

Run: `mvn -pl htht-admin -Dtest=ProcessingExecutionControllerTest,ResultDownloadTicketServiceTest test`

Expected: Tile tests pass and ZIP/individual artifact downloads remain unchanged.

- [ ] **Step 5: Review checkpoint.**

Use `git diff --check` and inspect test response bodies. The only browser-visible credential must be the opaque two-minute ticket in the relative tile URL.

## Task 5: Connect the Existing Cesium Layer UI to Descriptor Issuance

**Files:**
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Modify: `rsimageprocess-view/src/features/multisource/artifactMapPreview.ts`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/artifactMapPreview.test.ts`
- Test: `rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts`

**Consumes:** the POST `tile-preview` response.

**Produces:** an active Cesium imagery layer or a clear disabled/error message, without changing direct download.

- [ ] **Step 1: Write failing frontend contract tests.**

```ts
test("accepts only a relative authenticated tile template with WGS84 bounds", () => {
  expect(parseTilePreviewDescriptor({
    tileUrl: "/api/.../{z}/{x}/{y}.png?ticket=opaque",
    bounds: [110, 30, 111, 31], crs: "EPSG:4326", minZoom: 0, maxZoom: 16
  })).toMatchObject({ kind: "tiles" });
});

test("rejects a descriptor without a ticket, valid bounds, or accepted CRS", () => {
  expect(() => parseTilePreviewDescriptor({ tileUrl: "https://bad/{z}/{x}/{y}", bounds: [0, 0, 0, 1] }))
    .toThrow(/瓦片/);
});
```

- [ ] **Step 2: Run the focused tests to verify they fail.**

Run: `pnpm test:multisource -- --test-name-pattern "tile|map"`

Expected: FAIL because the descriptor API/parser and click-time request are absent.

- [ ] **Step 3: Implement descriptor retrieval and layer creation.**

```ts
export const getArtifactTilePreview = (taskId: ProcessingIdentifier, type: string) =>
  http.request<Result<ArtifactTilePreviewResponse>>(
    "post",
    `/api/multisource/processing-tasks/${taskId}/artifacts/${encodeURIComponent(type)}/tile-preview`
  );
```

In `showArtifactOnMap`, when `resolveArtifactView(artifact).mode === "map"` and the artifact format is `COG` or `GEOTIFF`, request the descriptor, validate it with `parseTilePreviewDescriptor`, then construct `Cesium.UrlTemplateImageryProvider`. Pass returned bounds to `viewer.camera.flyTo`; preserve the existing four-layer maximum, opacity control, remove, and clear behavior. Disable the View action for the current task session after a non-renderable response and expose the returned safe reason in its tooltip. Do not download the TIFF to the browser for map preview.

- [ ] **Step 4: Add style-visible layer labels.**

Use Worker descriptor `legend.kind` to display `RGB 影像`, `分类掩膜`, `连续评分图`, or `灰度栅格` in the map-layer list. The actual color mapping stays server-side, so Cesium loads only PNG tiles.

- [ ] **Step 5: Run frontend regression and build.**

Run:

```text
pnpm test:multisource
pnpm build
```

Expected: all multisource tests pass, and the production build succeeds.

## Task 6: Validate Real Artifacts and Operational Failure Modes

**Files:**
- Modify: `JavaRSproject/docs/deployment/multisource-backend-deployment.md`
- Test data: five registered artifacts from a completed HY-1C task: `czi-features.tif`, `czi-rgb.tif`, `czi-l2a.tif`, `mask.tif`, and `score.tif`.

**Consumes:** the end-to-end deployed Worker, backend, and frontend.

**Produces:** accepted/rejected artifact inventory, operator guidance, and deployment checks.

- [ ] **Step 1: Record the real-artifact eligibility matrix.**

For each of the five TIFFs, call `POST .../tile-preview` and record the public fields only:

| Artifact | Expected style | Required outcome |
| --- | --- | --- |
| `czi-features.tif` | RGB or multi-band | Tile descriptor or explicit CRS/range reason |
| `czi-rgb.tif` | RGB | Tile descriptor or explicit CRS/range reason |
| `czi-l2a.tif` | RGB/grayscale | Tile descriptor or explicit CRS/range reason |
| `mask.tif` | categorical mask | Tile descriptor with mask legend or explicit reason |
| `score.tif` | continuous score | Tile descriptor with continuous legend or explicit reason |

- [ ] **Step 2: Exercise map behavior in the browser.**

For each eligible artifact: click View, verify the map recenters to returned bounds, adjust opacity, load up to four layers, remove one layer, and clear all layers. Verify the original TIFF remains available through Download.

- [ ] **Step 3: Exercise failure modes.**

Verify: expired ticket gives a clear credential-expired notice; a modified `{z}/{x}/{y}` outside range returns `400`; another user's ticket returns `403`; missing CRS/range disables View but keeps Download; a non-PNG response is rejected by the frontend; repeated polling does not create tile tickets; more than four layers shows the existing limit message.

- [ ] **Step 4: Add deployment requirements.**

Document the required GDAL/rasterio drivers (`GTiff`, `COG`, `PNG`), Worker Python version, two-minute ticket TTL, cache size/TTL, and reverse-proxy rule that preserves query parameters on tile URLs. State that no public object-storage URL may be substituted for the protected tile route.

- [ ] **Step 5: Final verification checkpoint.**

Run:

```text
cd JavaRSproject/rs-algorithm-worker && python -m pytest -q tests/test_artifact_tiles.py tests/test_artifact_inspection.py
cd JavaRSproject && mvn -pl htht-admin test
cd rsimageprocess-view && pnpm test:multisource && pnpm build
git -C JavaRSproject diff --check
git -C rsimageprocess-view diff --check
```

Expected: all targeted tests/builds pass; any unrelated existing type-check failures are reported separately and are not hidden by this feature.

## Coverage Review

- Eligibility and COG/CRS/range validation: Task 1.
- RGB, multi-band, mask, and score raster rendering: Task 2.
- Per-artifact short-lived authorization, expiry, ownership, and caches: Task 3.
- Safe descriptor/tile HTTP contract without tickets in polling responses: Task 4.
- Existing layer list, opacity, cleanup, and direct-download integration: Task 5.
- Current five TIFF artifacts, permission expiry, malformed requests, and deployment acceptance: Task 6.

## Execution Handoff

Plan complete and saved to `JavaRSproject/docs/superpowers/plans/2026-09-11-cog-tiff-map-preview.md`.

1. **Subagent-Driven (recommended):** dispatch a fresh implementer per task with review checkpoints.
2. **Inline Execution:** execute the tasks in this session in order, with each task's tests before proceeding.
