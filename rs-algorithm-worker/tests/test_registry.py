import json
from pathlib import Path

import pytest

from rs_algorithm_worker.registry import dispatch


@pytest.mark.real_sample
def test_registry_returns_logical_uris_without_local_paths(gf_manifest):
    request = {
        "operation": "inspect",
        "requestId": "req-01",
        "payload": {"inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}}
                                 for item in gf_manifest["inputs"]]},
    }
    response = dispatch(request, gf_manifest)
    assert len(response["candidates"]) == 2
    encoded = json.dumps(response)
    assert "asset-manifest" not in encoded
    assert not any(str(Path(item["path"])) in encoded for item in gf_manifest["inputs"])
