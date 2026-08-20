# Local Worker Asset Manifest Design

**Status:** Approved for implementation

## Goal

Allow a local Worker to read only the assets authorized for one invocation and
write only to that invocation's controlled staging directory. The solution
keeps local paths out of API input, domain models, Worker request envelopes,
and database snapshots.

## Scope

This design extends the local Worker transport used by `inspect`, `build-plan`,
and `execute-stage`. It enables real local-file execution for the GF-6 browse
adapter and later product adapters. It does not add remote storage transport,
HTTP signed URLs, artifact registration, callback handling, state mutation, or
frontend integration.

## Invocation Files

The existing per-request directory gains an infrastructure-owned manifest and
an output staging directory:

```text
<work-root>/<request-id>/request.json
<work-root>/<request-id>/asset-manifest.json
<work-root>/<request-id>/response.json
<work-root>/<request-id>/stdout.log
<work-root>/<request-id>/stderr.log
<work-root>/<request-id>/output/
```

The Worker receives all three explicit arguments:

```text
--request <request.json>
--asset-manifest <asset-manifest.json>
--response <response.json>
```

`request.json` remains the versioned business envelope and contains only
logical `storageUri` values and grant references. `asset-manifest.json` is a
local infrastructure file generated after Java authorization; it is never
accepted from an API caller and is never persisted as a domain snapshot.

## Manifest Format

The manifest uses its own version `1.0` and is written atomically before the
Worker process starts:

```json
{
  "manifestVersion": "1.0",
  "requestId": "inspect-01",
  "grantReference": "read-01",
  "expiresAt": "2026-08-20T00:00:00Z",
  "inputs": [
    {
      "storageUri": "rs://input/GF6-WFV-LEVEL1A-1420733151.jpg",
      "role": "BROWSE_IMAGE",
      "ordinal": 0,
      "path": "C:\\worker-assets\\GF6-WFV-LEVEL1A-1420733151.jpg",
      "checksum": "sha256:..."
    }
  ],
  "output": {
    "path": "C:\\worker-work\\inspect-01\\output"
  }
}
```

`path` values are allowed only in this server-generated file. Each input path
must resolve to a regular file beneath a configured `assetRoot`. The output
path must resolve exactly beneath the invocation directory's `output`
directory. The Java manifest writer rejects duplicate `storageUri`, duplicate
`role + ordinal`, expired grants, unreadable inputs, path traversal, and any
path outside its configured root.

For `inspect` and `build-plan`, the grant comes from `AssetAccessGrant`. For
`execute-stage`, the manifest uses the `ExecutionGrant`, the immutable input
asset snapshot, and the logical output URI carried by `StageExecutionRequest`.
It creates the invocation output directory as a staging location for that
logical destination. A later orchestration slice validates and registers
artifacts from this directory.

## Java Boundaries

`LocalWorkerAssetResolver` is an infrastructure port that resolves a logical
`StorageUri` to a local path only after validating a grant reference. Its
production implementation is configured by `htht-admin` or deployment wiring;
it is not implemented in the domain module using arbitrary path conversion.

`LocalWorkerAssetManifestWriter` composes the resolver with the configured
work root. It creates a `WorkerAssetManifest` for one invocation and writes it
to the invocation directory. The manifest writer owns all local path handling.

`StageExecutionRequest` gains immutable `List<InputAsset> inputs` and
`StorageUri outputUri` fields. `WorkerInvocation` gains the manifest JSON or
an immutable manifest value.
`LocalWorkerProcessLauncher` writes `asset-manifest.json`, creates `output/`,
and passes `--asset-manifest`. It still treats only `response.json` as the
business result. `LocalProcessWorkerGateway` receives the manifest writer as
an injected infrastructure dependency and selects the read or execution grant
from the existing application-port request.

The production gateway continues to have no repository dependency and makes
no task-state changes.

## Worker Rules

The Worker validates manifest version, request ID, grant expiration, unique
input entries, and that every request payload input `storageUri` appears in the
manifest. It uses manifest paths only after those checks. It writes temporary
files and final staged artifacts only below `output.path`; an output path from
request JSON is ignored because none is valid input.

The GF-6 browse adapter derives its JPG, JGW, and JSON paths only by matching
the three authorized manifest inputs for a candidate. It does not inspect a
parent directory or discover unrelated files.

## Reliability And Security

- Java writes `request.json` and `asset-manifest.json` atomically before launch.
- A Worker receives a unique request directory and cannot be configured by API
  values to access another invocation directory.
- The resolver checks normalized and real paths beneath the configured asset
  root before writing a manifest entry, preventing symlink escape.
- The Worker rejects expired manifests before reading any input.
- On process failure or timeout, request, manifest, stdout, stderr, and staged
  output remain diagnostic evidence. They are removed only by an explicit
  retention policy outside the request path.
- No absolute path is returned in `response.json`, API responses, task state,
  candidate metadata, or database snapshots.

## Tests

1. The manifest writer maps only an authorized `StorageUri` to a configured
   local asset-root file and rejects an escaped or missing file.
2. Duplicate logical URI, duplicate role/ordinal, expired grant, and a
   different request ID are rejected before launching a Worker.
3. The launcher passes `--asset-manifest`, writes a manifest, and creates the
   output staging directory alongside the existing response-file handoff.
4. The Worker rejects a request whose logical inputs do not exactly match the
   manifest and cannot write outside `output.path`.
5. A real GF sample run uses `MULTISOURCE_GF_SAMPLE_ROOT` as the configured
   asset root, never an input path, and successfully reads only its three
   manifest-listed files.

## Deferred Decisions

Remote Worker asset delivery, object-storage signed URLs, artifact promotion,
checksum revalidation policy for large source files, output retention, and
task-state orchestration remain separate slices.
