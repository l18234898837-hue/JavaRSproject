# Multisource Artifact Experience Design

**Status:** Approved for implementation

## Goal

Let operators inspect every generated multisource artifact from the task
progress dialog, while retaining protected individual and ZIP downloads.

## Experience

The progress dialog has two collapsible regions. Execution details retain the
per-stage status, timestamps, failures, declared outputs, and generated
artifacts. Artifact list is a task-wide table grouped by stage and filterable
by all, final, and intermediate retention. Every row has View and Download.
View is disabled only when the server reports a concrete unsupported reason.

The completed task action issues the existing ZIP ticket and downloads all
registered task artifacts. Failed and cancelled tasks retain individual
downloads only.

## Artifact Capability Contract

Progress artifact objects expose `format` and `view` in addition to the
existing fields.

```json
{
  "format": "COG",
  "view": {
    "enabled": true,
    "mode": "map",
    "reason": null,
    "inspectionUrl": "/api/multisource/processing-tasks/42/artifacts/STANDARD_COG/inspection"
  }
}
```

Allowed modes are `map`, `image`, `json`, `table`, `dataset`, `text`, and
`disabled`. The backend derives them from the actual produced file name and
the declared artifact type. Frontend inference is only a compatibility
fallback for historical responses.

## Preview Behavior

- COG and GeoTIFF use a protected tile description and PNG tile endpoint.
- GeoJSON and coordinate CSV load a map layer and show an attribute table.
- Plain CSV uses a bounded table preview.
- NetCDF and Parquet return bounded metadata, statistics, and sample rows.
- JSON and image previews retain existing behavior.
- NPZ and unknown binary data are download-only.

Preview services never return absolute paths, source credentials, full binary
files, or browser-visible bearer tokens. Tile requests use a short-lived,
single-artifact ticket that is checked by the backend.

## Boundaries

The Java controller authorizes and locates an artifact through the existing
task ownership flow. The Python Worker performs bounded content inspection and
raster tile rendering with its existing rasterio, netCDF4, and pyarrow
dependencies. The old generic grid tile controller remains unchanged because
its layer identifiers and authorization model are unrelated to processing
task artifacts.
