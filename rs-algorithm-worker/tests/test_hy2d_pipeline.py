import io
import os
import tarfile
from pathlib import Path

import numpy as np
import pytest
from netCDF4 import Dataset

from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.operators.netcdf_track import decode_values, decode_tar_gz


def _write_nc(path: Path, *, format="NETCDF3_CLASSIC", length=3):
    with Dataset(path, "w", format=format) as ds:
        ds.createDimension("time", length)
        for name, values in {
            "lat": [10.0, 11.0, 12.0][:length],
            "lon": [120.0, 121.0, 122.0][:length],
            "alt": [700.0, 701.0, 702.0][:length],
            "range_ku": [690.0, 691.0, 692.0][:length],
            "range_corrected": [0.1, 0.1, 0.1][:length],
            "ocean_tide_sol1": [0.2, 0.2, 0.2][:length],
            "surface_type": [0, 0, 0][:length],
            "rain_flag": [0, 0, 0][:length],
            "ice_flag": [0, 0, 0][:length],
            "qual_alt_1hz_range_ku": [0, 0, 0][:length],
            "orb_state_flag": [0, 0, 0][:length],
        }.items():
            variable = ds.createVariable(name, "f8" if name not in {"surface_type", "rain_flag", "ice_flag", "qual_alt_1hz_range_ku", "orb_state_flag"} else "i4", ("time",))
            variable[:] = values
        scaled = ds.createVariable("scaled", "i2", ("time",), fill_value=-32768)
        scaled.scale_factor = 0.01
        scaled.add_offset = 1.0
        scaled.set_auto_scale(False)
        scaled[:] = np.array([100, -32768, 200], dtype=np.int16)


def _archive(tmp_path, members):
    package = tmp_path / "sample.tar.gz"
    with tarfile.open(package, "w:gz") as archive:
        for name, content in members:
            info = tarfile.TarInfo(name)
            info.size = len(content)
            archive.addfile(info, io.BytesIO(content))
    return package


def test_decode_variable_applies_scale_offset_and_fill_value():
    raw = np.array([100, -32768], dtype=np.int16)
    decoded = decode_values(raw, scale_factor=0.01, add_offset=1.0, fill_value=-32768)
    np.testing.assert_allclose(decoded[:1], [2.0])
    assert np.isnan(decoded[1])


def test_decode_tar_gz_reads_track_data_and_metadata(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    result = decode_tar_gz(package)
    assert isinstance(result, TrackData)
    assert result.latitude.shape == (3,)
    assert "alt" in result.fields
    assert np.isnan(result.fields["scaled"][1])
    assert result.metadata["netcdf_format"] == "NETCDF3_CLASSIC"


def test_decode_tar_gz_rejects_unsafe_member(tmp_path):
    package = _archive(tmp_path, [("../evil.nc", b"bad"), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="unsafe"):
        decode_tar_gz(package)


def test_decode_tar_gz_rejects_multiple_nc_or_missing_metadata(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("a.nc", nc.read_bytes()), ("b.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="exactly one"):
        decode_tar_gz(package)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes())])
    with pytest.raises(ValueError, match="meta.xml"):
        decode_tar_gz(package)


def test_decode_tar_gz_rejects_non_netcdf3_and_length_mismatch(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc, format="NETCDF4")
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="NETCDF3_CLASSIC"):
        decode_tar_gz(package)


@pytest.mark.real_sample
def test_real_hy2d_sample_read_only_smoke():
    root = os.getenv("MULTISOURCE_HY2D_SAMPLE_ROOT")
    if not root or not Path(root).is_dir():
        pytest.skip("MULTISOURCE_HY2D_SAMPLE_ROOT is not configured")
    packages = sorted(Path(root).glob("*.tar.gz"))
    if not packages:
        pytest.skip("HY-2D sample root contains no tar.gz package")
    result = decode_tar_gz(packages[0])
    assert isinstance(result, TrackData)
    assert result.latitude.size > 0
