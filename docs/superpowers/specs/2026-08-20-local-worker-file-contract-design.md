# Local Worker File Contract Design

**Status:** Approved for implementation

## Goal

Define a versioned, auditable contract between the Java multisource backend and
the local Python Worker. The contract supports product inspection, processing
plan construction, and single-stage execution without allowing Worker process
details or JSON transport types into the domain and application layers.

## Scope

This design adds the local transport boundary only. It does not add task
dispatch orchestration, Worker callback acceptance, leases, retries, recovery,
quality rules, or product-specific algorithms.

## Transport

Each invocation receives an isolated work directory named from a generated
request identifier:

```text
<work-root>/<request-id>/request.json
<work-root>/<request-id>/response.json
<work-root>/<request-id>/stdout.log
<work-root>/<request-id>/stderr.log
```

The Java gateway writes `request.json`, then launches the configured Worker
command with explicit `--request` and `--response` arguments. The Worker must
write a complete `response.json` atomically before exiting. The response file
is the only business result. Exit code, standard output, and standard error are
diagnostic evidence only.

The launcher enforces a configured timeout and terminates only the child
process it started. `cancel` terminates a locally tracked child process when it
exists; it does not change task or stage state.

## Envelope

All requests and responses use a JSON envelope:

```json
{
  "schemaVersion": "1.0",
  "operation": "inspect",
  "requestId": "...",
  "payload": {}
}
```

Allowed operations are `inspect`, `build-plan`, and `execute-stage`. The codec
rejects unsupported schema versions, unknown operations, blank request IDs,
missing payloads, duplicate JSON properties, and trailing JSON content.

## Request Payloads

`inspect` contains a preflight identifier, read-only input asset snapshots,
and a read grant. It returns every candidate recognized from the supplied
assets, including unsupported and unknown candidates.

`build-plan` contains a task identifier, one selected `SUPPORTED` candidate,
an immutable parameter snapshot, and a read grant. It returns a validated
processing plan and its processor version.

`execute-stage` contains the existing `StageExecutionRequest` fields: task and
stage identifiers, stage key, attempt, execution identifier, profile and
processor versions, parameter snapshot, and execution grant. Its output grant
must be restricted to the task's output prefix.

All asset locations remain controlled `storageUri` values or asset references.
No absolute local path, arbitrary command argument, credential, or unbounded
filesystem location is accepted from an API caller or carried in the domain
model.

## Response Payloads

Every response contains either a success payload or a structured failure:

```json
{
  "code": "UNSUPPORTED_PRODUCT",
  "message": "The product profile is not supported.",
  "retryable": false
}
```

On `inspect` success, the payload contains the rule package version and a
non-empty candidate list. On `build-plan` success, it contains the plan and
processor version. On `execute-stage` success, it contains the Worker ID,
stage outcome, produced artifacts with checksums, optional resource metrics,
and optional diagnostic log URIs.

The Java codec treats a Worker-declared failure with a valid response envelope
as a domain-relevant Worker failure. A nonzero exit code, timeout, missing
response, malformed JSON, invalid envelope, or response/request identifier
mismatch is a gateway protocol failure. The gateway must preserve both the
operation and request identifier in raised errors for audit and diagnosis.

## Java Boundaries

`WorkerContractCodec` is responsible only for deterministic envelope encoding
and strict response decoding. `WorkerProcessLauncher` owns work directories,
command construction, timeout handling, process lifecycle, and diagnostic
files. `LocalProcessWorkerGateway` implements `ProductInspector`,
`ProcessingPlanner`, and `TaskDispatcher` by composing those two components
and translating validated results to application port types.

The gateway does not mutate `rs_` task state, consume Worker event streams, or
persist artifacts. Those responsibilities remain in the later execution
orchestration slice, after durable event acceptance and lease rules are added.

## Security and Reliability

- The configured Worker executable and work root are server configuration,
  never request data.
- Each request uses a unique directory beneath the configured root; normalized
  paths must remain beneath that root.
- Request files are written before process launch. A successful process without
  an atomic valid response is a failure.
- Standard output and error are bounded diagnostic files and are never parsed
  as result data.
- Worker error codes are not swallowed or converted to success.
- The response request ID and operation must match the request exactly.

## Tests

Tests are written before production code and cover:

1. Versioned encoding for each operation and deterministic required fields.
2. Successful result translation for inspection, plan, and stage dispatch.
3. Worker-declared structured failure preservation.
4. Unknown schema version, malformed JSON, mismatched request ID, and missing
   required fields.
5. Launcher timeout, nonzero exit, missing response file, and successful
   response-file handoff using a test process.
6. Gateway behavior that confirms no task-state persistence or stdout result
   parsing is introduced.

## Deferred Decisions

Worker event streaming, callback authentication, artifact registration,
resource limits beyond the local timeout, remote Worker transport, and product
specific preprocessing semantics are explicitly deferred. They require the
later execution orchestration and product-adapter slices.
