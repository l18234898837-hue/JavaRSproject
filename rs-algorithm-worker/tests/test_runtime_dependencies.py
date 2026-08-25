import importlib.metadata


def test_common_raster_runtime_dependencies_are_declared():
    requirements = importlib.metadata.requires("rs-algorithm-worker") or []
    normalized = "\n".join(requirements).lower()

    for package in ("numpy", "rasterio", "pyproj", "scipy"):
        assert package in normalized
