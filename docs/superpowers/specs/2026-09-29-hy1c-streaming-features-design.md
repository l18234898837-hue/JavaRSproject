# HY-1C Streaming Features Design

## Goal

Allow HY-1C CZI feature extraction to process large cropped scenes without
materializing full-size feature arrays or all source bands in memory.

## Design

`EXTRACT_CZI_FEATURES` will use a HY-1C-specific streaming path instead of
loading `CROPPED_CZI` into `RasterData`. The compressed NPZ member containing
the science array will be decompressed into a request-local NPY file. NumPy
will then expose it as a read-only memory map, so source windows are backed by
disk rather than process memory.

The feature writer will open a tiled GeoTIFF and visit it in fixed-size output
windows. Each source band is read only for the current window plus a halo:
one pixel for gradients and `windowSize // 2` pixels for local variance. The
result writer emits each requested feature band one window at a time. At block
edges the algorithm uses the same nearest-neighbour boundary behavior as the
current SciPy implementation. Statistics and valid-pixel ratios are accumulated
while source windows are read.

The final GeoTIFF is converted to the existing COG output contract. Metadata,
feature names, NoData behavior, numerical values, and output artifact paths
remain unchanged. The existing in-memory `extract_features` API remains in
place for small rasters and non-HY-1C pipelines.

## Constraints

- Keep the existing HY-1C request, output URI, artifact type, and metadata
  contracts unchanged.
- Use only existing NumPy, SciPy, and Rasterio dependencies.
- Do not change unrelated uncommitted files.
- Match the current `gradient` and `local_variance` outputs on small fixtures,
  including NaN centers, invalid neighbours, image edges, and block seams.
- Source and feature data must be bounded by a fixed tile size rather than
  image dimensions.
