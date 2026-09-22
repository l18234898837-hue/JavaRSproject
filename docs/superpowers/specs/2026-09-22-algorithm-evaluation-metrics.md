# Algorithm Evaluation Metrics Contract

## Scope of this first slice

This slice defines a versioned metric catalog for the six supported product
profiles. It does not change processing algorithms, introduce automatic
threshold tuning, or claim scientific accuracy.

The implementation is in
`rs-algorithm-worker/src/rs_algorithm_worker/algorithm_evaluation.py`.

## Metric categories

- `CORRECTNESS`: format, dimensions, coordinates, and required structure.
- `DATA_QUALITY`: valid-pixel/point ratios, coverage, continuity, and residuals.
- `PERFORMANCE`: reserved for a later phase; processing time and resource usage
  will be recorded by the orchestration layer rather than inferred from files.

Each metric declares:

- direction: `PASS_FAIL`, `HIGHER_BETTER`, `LOWER_BETTER`, or `DESCRIPTIVE`;
- source: existing artifact metadata, reference control points, or labels;
- whether reference data is required;
- an optional threshold. The first version intentionally leaves business
  thresholds unset.

## Interpretation rule

`build_evaluation_contract()` means “these are the metrics to evaluate”.
`observe_artifact_metrics()` only maps already available inspection fields and
returns `UNAVAILABLE` when a field is absent. It never converts a file-health
observation into an accuracy score.

Scientific metrics such as navigation error, projection error, baseline
residual, anomaly precision, and anomaly recall remain unavailable until
reference control points, validated products, or human labels are supplied.

## Next bounded slice

The reference calculation primitives are implemented in
`rs-algorithm-worker/src/rs_algorithm_worker/reference_evaluation.py`.
They accept aligned arrays only and return JSON-safe MAE/RMSE/Bias,
geolocation error, or Precision/Recall/F1 values. They do not read task files,
change processing, or infer a threshold.

Reference input contracts are now defined in
`rs-algorithm-worker/src/rs_algorithm_worker/reference_inputs.py`:

- `gf6-control-points-v1`: unique control-point IDs with predicted and
  reference longitude/latitude pairs;
- `hy2d-labels-v1`: unique observation IDs with predicted/reference anomaly
  booleans.

The validators reject duplicate IDs, invalid coordinates, non-boolean labels,
empty samples, and oversized payloads. Evaluation results include the schema
and reference ID for provenance. Upload, authorization, and task integration
remain deliberately out of scope for this slice.

The next integration step should add a read-only evaluation artifact containing
observed metrics, reference provenance, parameter snapshot, algorithm version,
and explicit unavailable entries.
