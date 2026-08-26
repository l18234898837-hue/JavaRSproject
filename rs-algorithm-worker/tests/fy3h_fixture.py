from __future__ import annotations

from pathlib import Path

import h5py
import numpy as np


ROWS = 20
COLUMNS = 16
FRAMES = 2
ROWS_PER_FRAME = ROWS // FRAMES


def make_fy3h_pair(root: Path, *, time: str = "2335", orbit: int = 3948,
                   version: str = "V0") -> tuple[Path, Path]:
    observation = root / f"FY3H_MERSI_GRAN_L1_20260701_{time}_1000M_{version}.HDF"
    geo = root / f"FY3H_MERSI_GRAN_L1_20260701_{time}_GEO1K_{version}.HDF"
    attributes = {
        "Satellite Name": "FY-3H",
        "Sensor Identification Code": "MERSI",
        "Observing Beginning Date": "2026-07-01",
        "Observing Beginning Time": f"{time[:2]}:{time[2:]}:00.575",
        "Observing Ending Date": "2026-07-01",
        "Observing Ending Time": f"{time[:2]}:{time[2:]}:59.077",
        "Orbit Number": np.array([orbit], dtype=np.uint32),
        "Version Of Calibration Parameter": "V 1.0.1",
        "Version Of Software": "V 1.0.1",
    }
    with h5py.File(observation, "w") as handle:
        _write_root_attributes(handle, attributes)
        handle.attrs["EarthSun Distance Ratio"] = np.array([1.01659817], dtype=np.float64)
        handle.attrs["Solar_Irradiance"] = np.ones(19, dtype=np.float32)
        handle.attrs["Effect_Center_WaveLength"] = np.ones(25, dtype=np.float32)
        handle.attrs["TBB_Trans_Coefficient"] = np.ones(12, dtype=np.float32)
        data = handle.create_group("Data")
        for name, channels in (("EV_250_Aggr.1KM_RefSB", 4),
                               ("EV_250_Aggr.1KM_Emissive", 2),
                               ("EV_1KM_RefSB", 15),
                               ("EV_1KM_Emissive", 4)):
            dataset = data.create_dataset(name, data=np.zeros((channels, ROWS, COLUMNS), dtype=np.uint16))
            dataset.attrs["Slope"] = np.ones(channels, dtype=np.float32)
            dataset.attrs["Intercept"] = np.zeros(channels, dtype=np.float32)
            dataset.attrs["FillValue"] = np.uint16(65535)
            dataset.attrs["valid_range"] = np.array([0, 65534], dtype=np.uint16)
        calibration = handle.create_group("Calibration")
        calibration.create_dataset("VIS_Cal_Coeff", data=np.zeros((19, 3), dtype=np.float32))
        calibration.create_dataset("IR_Cal_Coeff", data=np.zeros((6, 4, FRAMES), dtype=np.float32))
        handle.create_group("QA").create_dataset("QA_Frame_Flag", data=np.zeros(FRAMES, dtype=np.uint16))
    with h5py.File(geo, "w") as handle:
        _write_root_attributes(handle, attributes)
        geolocation = handle.create_group("Geolocation")
        for name in ("Latitude", "Longitude", "SolarZenith", "SolarAzimuth", "SensorZenith", "SensorAzimuth"):
            geolocation.create_dataset(name, data=np.zeros((ROWS, COLUMNS), dtype=np.float32))
        geolocation.create_dataset("QA_GEO", data=np.zeros((FRAMES, 1), dtype=np.uint16))
        handle.create_group("Timedata").create_dataset("DayNightFlag", data=np.zeros((FRAMES, 1), dtype=np.uint8))
    return observation, geo


def _write_root_attributes(handle: h5py.File, attributes: dict[str, object]) -> None:
    for name, value in attributes.items():
        handle.attrs[name] = value
