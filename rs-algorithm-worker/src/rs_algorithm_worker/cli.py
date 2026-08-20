"""Command line envelope for the local algorithm Worker."""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path
from typing import Any

from .contracts import ProtocolError, validate_invocation
from .registry import dispatch


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--request", required=True)
    parser.add_argument("--asset-manifest", required=True)
    parser.add_argument("--response", required=True)
    args = parser.parse_args(argv)
    response_path = Path(args.response)
    try:
        request = _read_object(Path(args.request))
        manifest_path = Path(args.asset_manifest)
        manifest = _read_object(manifest_path)
        validate_invocation(request, manifest, manifest_path=manifest_path)
        response = {
            "schemaVersion": "1.0",
            "operation": request["operation"],
            "requestId": request["requestId"],
            "payload": dispatch(request, manifest),
        }
        _write_atomically(response_path, response)
        return 0
    except ProtocolError as error:
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {"code": "INVALID_ASSET_MANIFEST", "message": str(error), "retryable": False},
        }
        _write_atomically(response_path, response)
        return 2
    except (OSError, json.JSONDecodeError, ValueError) as error:
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {"code": "INVALID_REQUEST", "message": "invalid Worker input", "retryable": False},
        }
        _write_atomically(response_path, response)
        return 2


def _read_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ProtocolError("Worker document must be an object")
    return value


def _write_atomically(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as temporary:
            json.dump(value, temporary, ensure_ascii=True, separators=(",", ":"))
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def _safe_operation(request: Any) -> str:
    return request.get("operation", "unknown") if isinstance(request, dict) else "unknown"


def _safe_request_id(request: Any) -> str:
    return request.get("requestId", "unknown") if isinstance(request, dict) else "unknown"


if __name__ == "__main__":
    raise SystemExit(main())
