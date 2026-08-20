# GF-6 WFV Browse Adapter Design

**Status:** Approved for implementation

## Goal

Add a bounded fifth product profile for the supplied GF-6 WFV browse packages.
The profile supports real package inspection, metadata extraction, affine
georeferencing, and visual-image feature processing. It must never claim that
a browse JPEG is a radiometrically calibrated or rigorously geocoded GF-6 L1A
science product.

## Observed Sample Contract

`C:\Users\24257\Desktop\GIS开发\GF` contains two independent packages. Each
package has one common filename stem and exactly these files:

```text
GF6-WFV-LEVEL1A-<product-id>.jpg
GF6-WFV-LEVEL1A-<product-id>.jgw
GF6-WFV-LEVEL1A-<product-id>.json
```

The JSON identifies `satelliteid=GF6`, `sensorid=WFV`, `sublevel=LEVEL1A`, a
16 metre GSD, acquisition start and end time, a WGS84 longitude/latitude
polygon, cloud percentage, orbit metadata, and a browse image location. The
JPEG is an 8-bit RGB browse image. The JGW provides six affine coefficients;
its upper-left coordinate agrees with the JSON footprint. No original bands,
radiometric calibration data, RPC model, complete quality mask, or strict
geometry model is present.

The Worker splits the directory into one candidate per complete stem. It never
treats the directory as one candidate and it rejects partial or cross-stem
file sets.

## Product Profile And Capability Boundary

The Worker registers this product exactly as:

```text
productCode: GF-6-WFV-BROWSE
profileVersion: v1
dataShape: RASTER_GRID
supportStatus: SUPPORTED
```

`SUPPORTED` means supported for the explicit browse-image capability only. It
does not mean support for every GF-6 product or for quantitative remote-sensing
processing. The published capability is `BROWSE_VISUAL_FEATURES` and permits:

- converting the JPEG plus world file into a georeferenced display raster;
- image-space or RGB visual features such as texture, edge, shape, object, or
  semantic-segmentation outputs;
- approximate map coordinates derived from the affine world file;
- preview, crop, vector feature, and quality-report artifacts.

The profile must reject a plan or stage requesting radiometric calibration,
reflectance, band-ratio or spectral-index computation, atmospheric correction,
RPC/orthorectification, terrain correction, or scientific quality-mask
interpretation. The error code is `UNSUPPORTED_BROWSE_OPERATION` and is not
retryable.

An eventual original GF-6 WFV product is a separate future profile, for
example `GF-6-WFV-L1A`. It must not reuse this adapter or inherit this profile's
support state.

## Inspection And Metadata

The adapter validates all of the following before returning a candidate:

1. One readable JPEG, JGW, and JSON exist for the same stem.
2. JPEG dimensions are positive and the image is RGB or an explicitly handled
   JPEG color format.
3. JGW has exactly six finite decimal values and has non-zero determinant.
4. JSON satellite, sensor, product level, and product identifier agree with
   the filename family.
5. JSON acquisition interval is ordered, cloud percentage is in `[0, 100]`,
   and the footprint is a closed valid longitude/latitude polygon.
6. The JGW upper-left coordinate is inside the footprint bounding box with a
   documented tolerance suitable for browse products. A disagreement makes the
   package `UNSUPPORTED`, not a silently georeferenced candidate.

The returned candidate has three input asset roles: `BROWSE_IMAGE`,
`GEOREFERENCE`, and `METADATA`. Its metadata snapshot includes the product and
scene IDs, acquisition interval, WGS84 footprint, GSD, cloud percentage,
orbit, station, image size, JGW coefficients, and `georeferencingMethod` set
to `AFFINE_WORLD_FILE_APPROXIMATE`.

## Contract And Persistence Changes

The existing Worker envelope remains at schema version `1.0`; this is an
additive payload change, not an envelope version change. Each inspection
candidate gains a required `metadataSnapshot` JSON object. Java maps that
object without interpreting product-specific keys.

`ProductPackage` and `ProductCandidate` gain an immutable metadata snapshot.
`rs_batch_product` and `rs_task_product` gain a non-null JSONB
`metadata_snapshot` column with an object check. Existing records receive
`{}` in the forward-only migration. The persistence mapper round-trips the
snapshot unchanged. This generalizes the existing four real product profiles;
it is not GF-specific database schema.

The seed migration publishes `GF-6-WFV-BROWSE` as `SUPPORTED` and publishes
only `BROWSE_VISUAL_FEATURES` for that profile. No capability row represents
calibration, spectral, or strict-geometric processing for this profile.

## Worker Plans

For `build-plan`, the Worker accepts only a candidate with this exact profile
and creates a versioned DAG containing:

```text
validate-browse -> georeference-browse -> extract-visual-features -> write-browse-report
```

The first release implements the minimum executable boundary: validation,
world-file georeference preparation, and a deterministic visual-feature report
from the supplied image. Model-based segmentation and object detection remain
optional parameters and must fail explicitly when their configured model is
absent; no model is downloaded or selected from caller input.

`execute-stage` may only execute stages in that plan. The Worker writes all
temporary data under its invocation directory and returns only contract-defined
artifact references and checksums. Task state, artifact registration, and
event persistence remain responsibilities of the later Java execution
orchestration slice.

## Testing And Sample Handling

Tests use a small committed synthetic package for malformed-file cases and the
real `GF` samples only through the required `MULTISOURCE_GF_SAMPLE_ROOT`
environment variable. The real JPEGs and metadata do not enter Git.

Required tests cover:

1. Each real sample stem yields one `GF-6-WFV-BROWSE` candidate with
   `RASTER_GRID`, all three file roles, and its metadata snapshot.
2. A directory with both real sample stems returns two candidates.
3. Missing companion file, non-finite JGW coefficient, mismatched JSON
   satellite/sensor, invalid cloud value, and invalid footprint are rejected.
4. The plan contains only the four browse stages in dependency order.
5. A scientific operation request is rejected with
   `UNSUPPORTED_BROWSE_OPERATION`.
6. Java codec, translator, domain, persistence mapping, and disposable
   schema tests preserve a candidate metadata snapshot unchanged.

## Non-Goals

- No claim of radiometric calibration, reflectance, spectral indices,
  atmospheric correction, RPC correction, orthorectification, or scientific
  quality interpretation.
- No frontend work or frontend/backend integration.
- No changes to the task lifecycle, dispatch orchestration, callback handling,
  leases, retries, or artifact registration.
- No copying of external GF samples into the repository.
