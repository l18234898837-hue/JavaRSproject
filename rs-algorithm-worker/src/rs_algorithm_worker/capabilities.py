from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable

from .models import ProductContext


class CapabilityStatus(str, Enum):
    SUPPORTED = "SUPPORTED"
    NOT_SUPPORTED = "NOT_SUPPORTED"
    NOT_CHECKED = "NOT_CHECKED"


@dataclass(frozen=True)
class CapabilitySpec:
    name: str
    version: str
    supported_shapes: frozenset[str]
    required_bands: tuple[str, ...]


@dataclass(frozen=True)
class CapabilityResult:
    status: CapabilityStatus
    reason: str


class CapabilityRegistry:
    def __init__(self, specs: Iterable[CapabilitySpec] = ()) -> None:
        self._specs = {(spec.name, spec.version): spec for spec in specs}

    def check(
        self,
        name: str,
        version: str,
        context: ProductContext,
        available_bands: tuple[str, ...],
    ) -> CapabilityResult:
        spec = self._specs.get((name, version))
        if spec is None:
            return CapabilityResult(CapabilityStatus.NOT_CHECKED, "capability is not registered")
        if context.data_shape not in spec.supported_shapes:
            return CapabilityResult(CapabilityStatus.NOT_SUPPORTED, "data shape is not supported")
        missing = set(spec.required_bands).difference(available_bands)
        if missing:
            return CapabilityResult(CapabilityStatus.NOT_SUPPORTED, f"missing required bands: {', '.join(sorted(missing))}")
        return CapabilityResult(CapabilityStatus.SUPPORTED, "capability prerequisites are satisfied")


COMMON_CAPABILITIES = CapabilityRegistry((
    CapabilitySpec("features", "statistics-v1", frozenset({"RASTER_GRID", "SWATH_RASTER"}), ()),
    CapabilitySpec("fusion", "percentile-rgb-v1", frozenset({"RASTER_GRID"}), ("B1", "B2", "B3")),
    CapabilitySpec("saliency", "contrast-v1", frozenset({"RASTER_GRID"}), ()),
))
