import os

from rs_algorithm_worker.geospatial_runtime import configure_geospatial_runtime


def test_configure_geospatial_runtime_uses_rasterio_data_directories(monkeypatch):
    monkeypatch.setenv("PROJ_LIB", r"D:\\PostgreSQL\\17\\share\\proj")
    monkeypatch.setenv("GDAL_DATA", r"D:\\PostgreSQL\\17\\gdal-data")

    configure_geospatial_runtime()

    assert os.environ["PROJ_LIB"].endswith("rasterio\\proj_data")
    assert os.environ["GDAL_DATA"].endswith("rasterio\\gdal_data")


def test_worker_import_configures_proj_before_rasterio_resolves_epsg():
    from rasterio.crs import CRS

    assert CRS.from_user_input("EPSG:4326").to_string() == "EPSG:4326"
