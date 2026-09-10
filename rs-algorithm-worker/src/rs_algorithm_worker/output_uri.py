from __future__ import annotations

import re
from typing import Any


LOGICAL_OUTPUT_URI = re.compile(
    r"rs://output/[A-Za-z0-9][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*/?"
)


def logical_output_uri(value: Any) -> str:
    if not isinstance(value, str) or not LOGICAL_OUTPUT_URI.fullmatch(value):
        raise ValueError("logical output URI is required")
    segments = value.removeprefix("rs://output/").rstrip("/").split("/")
    if any(segment in {".", ".."} for segment in segments):
        raise ValueError("logical output URI is required")
    return value.rstrip("/")
