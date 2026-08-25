from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Protocol

from rs_algorithm_worker.models import ProductContext


@dataclass(frozen=True)
class OperatorResult:
    outputs: Mapping[str, Path]
    statistics: Mapping[str, Any]
    warnings: tuple[str, ...] = ()


class Operator(Protocol):
    name: str
    version: str

    def validate(self, context: ProductContext, parameters: Mapping[str, Any]) -> None: ...

    def execute(
        self,
        context: ProductContext,
        inputs: Mapping[str, Any],
        parameters: Mapping[str, Any],
        output_dir: Path,
    ) -> OperatorResult: ...
