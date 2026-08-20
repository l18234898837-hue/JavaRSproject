"""Operation dispatch kept behind the validated Worker boundary."""

from __future__ import annotations

from typing import Any


def dispatch(request: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    operation = request["operation"]
    if operation == "inspect":
        return {"rulePackageVersion": "rules-2026.08", "candidates": []}
    if operation == "build-plan":
        return {"version": "plan-v1", "stages": [{
            "stageKey": "VALIDATE_SOURCE",
            "stageCode": "VALIDATE_SOURCE",
            "dependsOnStageKeys": [],
        }]}
    if operation == "execute-stage":
        return {"workerId": "worker-local"}
    raise ValueError(f"unsupported operation: {operation}")
