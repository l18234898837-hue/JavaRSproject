from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import h5py


@dataclass(frozen=True)
class BandMapping:
    band_id: str
    wavelength_nm: int
    path: str
    scale_attribute: str
    offset_attribute: str
    fill_value_attribute: str
    valid_min_attribute: str
    valid_max_attribute: str


@dataclass(frozen=True)
class ReviewedL2AMapping:
    mapping_version: str
    bands: tuple[BandMapping, ...]
    wavelength_path: str
    longitude_path: str
    latitude_path: str
    navigation_scale_attribute: str
    navigation_offset_attribute: str
    quality_flags_path: str
    quality_flag_names_attribute: str
    flag_bits: tuple[tuple[str, int], ...]
    default_bad_flag_names: tuple[str, ...]
    required_global_attributes: dict[str, Any]

    @property
    def band_ids(self) -> tuple[str, ...]:
        return tuple(band.band_id for band in self.bands)


def load_reviewed_mapping() -> ReviewedL2AMapping:
    fixture = Path(__file__).resolve().parents[3] / "tests" / "fixtures" / "hy1c_czi_l2a_v1.json"
    try:
        payload = json.loads(fixture.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("HY-1C CZI L2A reviewed mapping is unreadable") from error

    bands = tuple(
        BandMapping(
            band_id=item["id"], wavelength_nm=item["wavelengthNm"], path=item["path"],
            scale_attribute=item["scaleAttribute"], offset_attribute=item["offsetAttribute"],
            fill_value_attribute=item["fillValueAttribute"], valid_min_attribute=item["validMinAttribute"],
            valid_max_attribute=item["validMaxAttribute"],
        )
        for item in payload["bands"]
    )
    navigation = payload["longitude"]
    quality = payload["quality"]
    mapping = ReviewedL2AMapping(
        mapping_version=payload["mappingVersion"], bands=bands, wavelength_path=payload["wavelengthPath"],
        longitude_path=navigation["path"], latitude_path=payload["latitude"]["path"],
        navigation_scale_attribute=navigation["scaleAttribute"],
        navigation_offset_attribute=navigation["offsetAttribute"], quality_flags_path=quality["path"],
        quality_flag_names_attribute=quality["flagNamesAttribute"],
        flag_bits=tuple((item["name"], item["bit"]) for item in quality["flagBits"]),
        default_bad_flag_names=tuple(quality["policies"]["default"]),
        required_global_attributes=dict(payload["requiredGlobalAttributes"]),
    )
    _validate_mapping(mapping)
    return mapping


def validate_source_against_mapping(h5_path: str | Path, mapping: ReviewedL2AMapping) -> None:
    with h5py.File(h5_path, "r") as handle:
        for name, expected in mapping.required_global_attributes.items():
            value = _attribute_value(handle.attrs, name)
            if value != expected:
                raise ValueError(f"HY-1C CZI L2A global attribute {name!r} does not match reviewed mapping")

        shape = None
        for band in mapping.bands:
            dataset = _required_dataset(handle, band.path)
            _require_attributes(dataset, band.scale_attribute, band.offset_attribute, band.fill_value_attribute,
                                band.valid_min_attribute, band.valid_max_attribute)
            shape = _same_shape(shape, dataset.shape, band.path)

        for path in (mapping.longitude_path, mapping.latitude_path, mapping.quality_flags_path):
            dataset = _required_dataset(handle, path)
            shape = _same_shape(shape, dataset.shape, path)
        _require_attributes(_required_dataset(handle, mapping.longitude_path), mapping.navigation_scale_attribute,
                            mapping.navigation_offset_attribute)
        _require_attributes(_required_dataset(handle, mapping.latitude_path), mapping.navigation_scale_attribute,
                            mapping.navigation_offset_attribute)
        flags = _required_dataset(handle, mapping.quality_flags_path)
        actual_names = _decode(_attribute_value(flags.attrs, mapping.quality_flag_names_attribute)).split(",")
        expected_names = [name for name, _ in mapping.flag_bits]
        if actual_names != expected_names:
            raise ValueError("HY-1C CZI L2A quality flag names do not match reviewed mapping")
        wavelengths = _required_dataset(handle, mapping.wavelength_path)[...].reshape(-1).tolist()
        if wavelengths != [band.wavelength_nm for band in mapping.bands]:
            raise ValueError("HY-1C CZI L2A wavelength order does not match reviewed mapping")


def _validate_mapping(mapping: ReviewedL2AMapping) -> None:
    if mapping.band_ids != ("B460", "B560", "B650", "B825") or len(set(mapping.band_ids)) != len(mapping.bands):
        raise ValueError("HY-1C CZI L2A mapping must declare the four unique CZI bands")
    paths = [mapping.wavelength_path, mapping.longitude_path, mapping.latitude_path, mapping.quality_flags_path]
    paths.extend(band.path for band in mapping.bands)
    if any(not path.startswith("/") for path in paths):
        raise ValueError("HY-1C CZI L2A HDF5 paths must be absolute")
    if not mapping.default_bad_flag_names or not mapping.flag_bits:
        raise ValueError("HY-1C CZI L2A quality policy must not be empty")
    known = {name for name, _ in mapping.flag_bits}
    if not set(mapping.default_bad_flag_names).issubset(known):
        raise ValueError("HY-1C CZI L2A quality policy references an unknown flag")
    if [bit for _, bit in mapping.flag_bits] != list(range(32)):
        raise ValueError("HY-1C CZI L2A flag bits must cover bits zero through thirty-one")


def _required_dataset(handle: h5py.File, path: str) -> h5py.Dataset:
    node = handle.get(path)
    if not isinstance(node, h5py.Dataset):
        raise ValueError(f"HY-1C CZI L2A dataset is missing: {path}")
    return node


def _require_attributes(dataset: h5py.Dataset, *names: str) -> None:
    for name in names:
        if name not in dataset.attrs:
            raise ValueError(f"HY-1C CZI L2A dataset {dataset.name} lacks attribute {name}")


def _same_shape(expected: tuple[int, ...] | None, actual: tuple[int, ...], path: str) -> tuple[int, ...]:
    if len(actual) != 2 or not all(size > 0 for size in actual):
        raise ValueError(f"HY-1C CZI L2A dataset {path} must be a non-empty raster")
    if expected is not None and actual != expected:
        raise ValueError(f"HY-1C CZI L2A dataset {path} shape differs from band data")
    return actual


def _attribute_value(attributes: h5py.AttributeManager, name: str) -> Any:
    if name not in attributes:
        raise ValueError(f"HY-1C CZI L2A attribute is missing: {name}")
    return _decode(attributes[name])


def _decode(value: Any) -> Any:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "item"):
        return _decode(value.item())
    return value
