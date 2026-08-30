from __future__ import annotations

from pathlib import Path

import h5py
import numpy as np


ROLE_ORDER = ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M")
CHANNELS_BY_ROLE = {
    "L1_0500M": (2,),
    "L1_1000M": (1, 2, 3),
    "L1_2000M": tuple(range(1, 9)),
    "L1_4000M": tuple(range(1, 16)),
}
SHAPES = {
    "L1_0500M": (21984, 21984),
    "L1_1000M": (10992, 10992),
    "L1_2000M": (5496, 5496),
    "L1_4000M": (2748, 2748),
    "GEO_4000M": (2748, 2748),
}


def make_fy4b_package(
    root: Path, *, end_by_role: dict[str, str] | None = None,
    missing_datasets: tuple[str, ...] = (),
) -> dict:
    end_by_role = end_by_role or {}
    inputs = []
    for ordinal, role in enumerate(ROLE_ORDER):
        end = end_by_role.get(role, "20260701001459")
        path = root / _filename(role, end)
        with h5py.File(path, "w") as handle:
            _root_attributes(handle, end)
            if role == "GEO_4000M":
                _write_geo(handle, missing_datasets)
            else:
                _write_l1(handle, role, missing_datasets)
        inputs.append({"path": str(path), "role": "UNCLASSIFIED", "ordinal": ordinal,
                       "storageUri": f"file:///{path.name}"})
    return {"inputs": inputs}


def _filename(role: str, end: str) -> str:
    kind, resolution = ("GEO", "4000M") if role == "GEO_4000M" else ("FDI", role.removeprefix("L1_"))
    return f"FY4B-_AGRI--_N_DISK_1050E_L1-_{kind}-_MULT_NOM_20260701000000_{end}_{resolution}_V0001.HDF"


def _root_attributes(handle: h5py.File, end: str) -> None:
    handle.attrs["Satellite Name"] = "FY-4B"
    handle.attrs["Sensor Name"] = "AGRI"
    handle.attrs["Dataset Name"] = "MULT"
    handle.attrs["OBIType"] = "DISK"
    handle.attrs["Observing Beginning Date"] = "2026-07-01"
    handle.attrs["Observing Beginning Time"] = "00:00:00"
    handle.attrs["Observing Ending Date"] = "2026-07-01"
    handle.attrs["Observing Ending Time"] = f"00:14:{end[-2:]}"
    handle.attrs["Data Quality"] = np.array([0], dtype=np.uint8)


def _write_l1(handle: h5py.File, role: str, missing_datasets: tuple[str, ...]) -> None:
    data = handle.create_group("Data")
    calibration = handle.create_group("Calibration")
    observation = handle.create_group("NOMObs")
    observation.create_dataset("NOMObsTime", data=np.zeros((1,), dtype=np.uint32))
    for channel in CHANNELS_BY_ROLE[role]:
        data_name = f"NOMChannel{channel:02d}"
        calibration_name = f"CALChannel{channel:02d}"
        if f"Data/{data_name}" not in missing_datasets:
            data.create_dataset(data_name, shape=SHAPES[role], dtype=np.uint16, chunks=(256, 256), fillvalue=0)
        if f"Calibration/{calibration_name}" not in missing_datasets:
            calibration.create_dataset(calibration_name, data=np.arange(16, dtype=np.float32))


def _write_geo(handle: h5py.File, missing_datasets: tuple[str, ...]) -> None:
    navigation = handle.create_group("Navigation")
    for name in ("LineNumber", "ColumnNumber", "NOMSatelliteZenith", "NOMSatelliteAzimuth",
                 "NOMSunZenith", "NOMSunAzimuth"):
        if f"Navigation/{name}" not in missing_datasets:
            navigation.create_dataset(name, shape=SHAPES["GEO_4000M"],
                                      dtype=np.int16 if name in {"LineNumber", "ColumnNumber"} else np.float32,
                                      chunks=(256, 256), fillvalue=-1 if name in {"LineNumber", "ColumnNumber"} else 0)
    qa = handle.create_group("QA")
    qa.create_dataset("NavQualityFlag", data=np.zeros((15,), dtype=np.uint8))
