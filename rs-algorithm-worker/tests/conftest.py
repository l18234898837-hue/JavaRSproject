import os
from pathlib import Path

import pytest


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
def fy4b_manifest():
    value = os.getenv("MULTISOURCE_FY4B_SAMPLE_FILE")
    if not value or not Path(value).is_file():
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_FILE is not configured")
    return {"inputs": [{"path": value, "role": "MAIN", "ordinal": 0,
                         "storageUri": f"file:///{Path(value).name}"}]}


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
