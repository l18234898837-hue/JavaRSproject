import os
import re
from pathlib import Path

import pytest


# Rasterio must use the compatible PROJ database bundled with this test venv.
_PROJ_DATA = Path(__file__).parents[1] / ".venv" / "Lib" / "site-packages" / "rasterio" / "proj_data"
if _PROJ_DATA.is_dir():
    os.environ["PROJ_LIB"] = str(_PROJ_DATA)


FY4B_FILENAME = re.compile(
    r"^FY4B-_AGRI--_N_DISK_(?P<subsat>[^_]+)_L1-_(?P<kind>FDI|GEO)-_MULT_NOM_"
    r"(?P<start>\d{14})_(?P<end>\d{14})_(?P<resolution>0500M|500M|1000M|2000M|4000M)_"
    r"(?P<version>V[^.]+)\.HDF$",
    re.IGNORECASE,
)
FY4B_ROLE_BY_KIND_RESOLUTION = {
    ("FDI", "0500M"): "L1_0500M", ("FDI", "500M"): "L1_0500M",
    ("FDI", "1000M"): "L1_1000M",
    ("FDI", "2000M"): "L1_2000M", ("FDI", "4000M"): "L1_4000M",
    ("GEO", "4000M"): "GEO_4000M",
}
FY4B_ROLE_ORDER = ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M")


def _path_env(name):
    value = os.getenv(name)
    return Path(value) if value else None


@pytest.fixture
def fy3h_manifest():
    root = _path_env("MULTISOURCE_FY3H_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_FY3H_SAMPLE_ROOT is not configured")
    return {"inputs": [{"path": str(path), "role": "MAIN", "ordinal": index,
                         "storageUri": f"file:///{path.name}"}
                        for index, path in enumerate(sorted(root.glob("*.HDF")))]}


@pytest.fixture
def fy3h_mersi_manifest():
    root = _path_env("MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT is not configured")
    observations = sorted(root.glob("*_1000M_*.HDF"))
    for observation in observations:
        geo = observation.with_name(observation.name.replace("_1000M_", "_GEO1K_"))
        if geo.is_file():
            return {"inputs": [
                {"path": str(observation), "role": "MAIN", "ordinal": 0,
                 "storageUri": f"file:///{observation.name}"},
                {"path": str(geo), "role": "MAIN", "ordinal": 1,
                 "storageUri": f"file:///{geo.name}"},
            ]}
    pytest.skip("MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT lacks matching GEO1K")


@pytest.fixture
def hy1c_manifest():
    root = _path_env("MULTISOURCE_HY1C_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_HY1C_SAMPLE_ROOT is not configured")
    return {"inputs": [{"path": str(path), "role": role, "ordinal": index,
                         "storageUri": f"file:///{path.name}"}
                        for index, (role, path) in enumerate([
                            ("MAIN", next(root.glob("*.h5"))),
                            ("BROWSE", next(root.glob("*.jpg"))),
                            ("METADATA", next(root.glob("*.xml"))),
                        ])]}


@pytest.fixture
def hy1c_l2a_manifest():
    root = _path_env("MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT is not configured")
    required = (("MAIN", ("*.h5", "*.H5")), ("METADATA", ("*.xml", "*.XML")),
                ("BROWSE", ("*.jpg", "*.JPG", "*.jpeg", "*.JPEG")))
    inputs = []
    for ordinal, (role, patterns) in enumerate(required):
        path = next((candidate for pattern in patterns for candidate in root.glob(pattern)), None)
        if path is None:
            pytest.skip(f"MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT lacks {role}")
        inputs.append({"path": str(path), "role": role, "ordinal": ordinal,
                       "storageUri": f"file:///{path.name}"})
    return {"inputs": inputs}


@pytest.fixture
def fy4b_manifest():
    root = _path_env("MULTISOURCE_FY4B_SAMPLE_ROOT")
    if root is None or not root.is_dir():
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_ROOT is not configured")
    groups = group_fy4b_hdfs(root.rglob("*.HDF"))
    paths = select_complete_fy4b_package(groups, os.getenv("MULTISOURCE_FY4B_SAMPLE_PACKAGE_ID"))
    if paths is None:
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_ROOT lacks one selected complete five-file package")
    return {"inputs": [
        {"path": str(path), "role": "UNCLASSIFIED", "ordinal": index,
         "storageUri": f"file:///{path.name}"}
        for index, path in enumerate(paths)
    ]}


def group_fy4b_hdfs(paths):
    """Group recursively discovered files by filename package identity."""
    groups = {}
    for path in paths:
        match = FY4B_FILENAME.fullmatch(Path(path).name)
        if match is None:
            continue
        key = (match["subsat"], match["start"], match["end"], match["version"])
        role = FY4B_ROLE_BY_KIND_RESOLUTION.get((match["kind"].upper(), match["resolution"].upper()))
        if role is None:
            continue
        groups.setdefault(key, {}).setdefault(role, []).append(Path(path))
    return groups


def select_complete_fy4b_package(groups, package_id=None):
    complete = []
    for key, role_map in groups.items():
        if set(role_map) != set(FY4B_ROLE_ORDER) or any(len(role_map[role]) != 1 for role in FY4B_ROLE_ORDER):
            continue
        candidate_id = "_".join(key)
        if package_id is not None and package_id != candidate_id:
            continue
        complete.append((candidate_id, [role_map[role][0] for role in FY4B_ROLE_ORDER]))
    if package_id is not None:
        return complete[0][1] if len(complete) == 1 else None
    return complete[0][1] if len(complete) == 1 else None


@pytest.fixture
def hy2d_manifest():
    root = _path_env("MULTISOURCE_HY2D_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_HY2D_SAMPLE_ROOT is not configured")
    return {"inputs": [{"path": str(path), "role": "MAIN", "ordinal": index,
                         "storageUri": f"file:///{path.name}"}
                        for index, path in enumerate(sorted(root.glob("*.tar.gz")))]}


@pytest.fixture
def gf_manifest():
    root = _path_env("MULTISOURCE_GF_SAMPLE_ROOT")
    if root is None or not root.exists():
        pytest.skip("MULTISOURCE_GF_SAMPLE_ROOT is not configured")
    inputs = []
    for index, jpg in enumerate(sorted(root.glob("*.jpg"))):
        for role, path in (("BROWSE_IMAGE", jpg), ("GEOREFERENCE", jpg.with_suffix(".jgw")),
                           ("METADATA", jpg.with_suffix(".json"))):
            inputs.append({"path": str(path), "role": role, "ordinal": index,
                           "storageUri": f"file:///{path.name}"})
    return {"inputs": inputs}
