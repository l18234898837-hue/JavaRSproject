"""Command line envelope for the local algorithm Worker."""

from __future__ import annotations

import argparse
import json
import os
import re
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
    except json.JSONDecodeError:
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {"code": "INVALID_REQUEST", "message": "invalid Worker input", "retryable": False},
        }
        _write_atomically(response_path, response)
        return 2
    except OSError:
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {"code": "INVALID_REQUEST", "message": "invalid Worker input", "retryable": False},
        }
        _write_atomically(response_path, response)
        return 2
    except MemoryError:
        # NumPy 的 ArrayMemoryError 是 MemoryError 子类；必须返回协议错误，
        # 否则后端只能看到进程退出，无法向用户说明真实限制。
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {
                "code": "MEMORY_LIMIT_EXCEEDED",
                "message": "影像处理内存不足，请缩小 ROI、减少波段或使用分块处理配置",
                "retryable": False,
            },
        }
        _write_atomically(response_path, response)
        return 2
    except ValueError as error:
        response = {
            "schemaVersion": "1.0",
            "operation": _safe_operation(locals().get("request")),
            "requestId": _safe_request_id(locals().get("request")),
            "error": {"code": _validation_error_code(error), "message": _safe_validation_message(error), "retryable": False},
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


def _safe_validation_message(error: ValueError) -> str:
    message = re.split(r"(?i)\btraceback\b", str(error), maxsplit=1)[0]
    message = re.sub(r"file:///[^\s\"']+", "<local path>", message, flags=re.IGNORECASE)
    message = re.sub(
        r"(?i)(?<![\w.])(?:\\\\\?\\[a-z]:[\\/][^\r\n\"']+|\\\\[^\r\n\"']+|[a-z]:[\\/][^\r\n\"']+)",
        "<local path>", message,
    )
    message = re.sub(r"(?<![:\w.-])/[^\s\"']+", "<local path>", message)
    return " ".join(message.split()) or "invalid Worker input"


def _validation_error_code(error: ValueError) -> str:
    if str(error).startswith("GF6_OUTPUT_LIMIT_EXCEEDED:"):
        return "GF6_OUTPUT_LIMIT_EXCEEDED"
    return "INVALID_REQUEST"


if __name__ == "__main__":
    raise SystemExit(main())
