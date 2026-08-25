from __future__ import annotations

from typing import Protocol


class Algorithm(Protocol):
    name: str
    version: str
