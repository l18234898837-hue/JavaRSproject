from __future__ import annotations

import json
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from importlib import resources
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
    fixture = resources.files("rs_algorithm_worker").joinpath("resources", "hy1c_czi_l2a_v1.json")
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
            if not _matches_expected_attribute(value, expected):
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


def inspect_l2a_manifest(manifest: dict[str, Any]) -> dict[str, Any]:
    paths_by_role = _manifest_paths_by_role(manifest)
    main = _single_role_path(paths_by_role, "MAIN", {".h5", ".hdf5"})
    metadata = _single_role_path(paths_by_role, "METADATA", {".xml"})
    browse = _single_role_path(paths_by_role, "BROWSE", {".jpg", ".jpeg"})
    mapping = load_reviewed_mapping()
    validate_source_against_mapping(main, mapping)
    try:
        root = ET.parse(metadata).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError("HY-1C CZI L2A XML metadata is unreadable") from error
    with h5py.File(main, "r") as handle:
        product_name = _decode(_attribute_value(handle.attrs, "Product Name"))
        if "_CZI_L2A_" not in product_name:
            raise ValueError("HY-1C CZI L2A product evidence is required")
        longitude = float(_decode(_attribute_value(handle.attrs, "Scene Center Longitude")))
        latitude = float(_decode(_attribute_value(handle.attrs, "Scene Center Latitude")))
    return {
        "packageId": product_name,
        "profileKey": {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"},
        "dataShape": "SWATH_RASTER",
        "supportStatus": "SUPPORTED",
        "files": [
            {"role": "MAIN", "ordinal": 0, "path": str(main)},
            {"role": "METADATA", "ordinal": 0, "path": str(metadata)},
            {"role": "BROWSE", "ordinal": 0, "path": str(browse)},
        ],
        "metadataSnapshot": {
            "mappingVersion": mapping.mapping_version,
            "xmlRoot": root.tag.rsplit("}", 1)[-1],
            "sceneCentre": {"longitude": longitude, "latitude": latitude},
        },
        "confidence": 1.0,
    }


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    return [inspect_l2a_manifest(manifest)]


def has_l2a_product_evidence(manifest: dict[str, Any]) -> bool:
    try:
        paths_by_role = _manifest_paths_by_role(manifest)
        main = _single_role_path(paths_by_role, "MAIN", {".h5", ".hdf5"})
        with h5py.File(main, "r") as handle:
            product_name = _decode(_attribute_value(handle.attrs, "Product Name"))
        return isinstance(product_name, str) and "_CZI_L2A_" in product_name
    except (OSError, ValueError):
        return False


def build_l2a_plan(candidate: dict[str, Any], parameter_snapshot: dict[str, Any]) -> dict[str, Any]:
    if candidate.get("profileKey") != {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"}:
        raise ValueError("HY-1C CZI L2A candidate is required")
    from ..l2a_profile import parse_l2a_parameters

    centre = candidate.get("metadataSnapshot", {}).get("sceneCentre", {})
    parameters = parse_l2a_parameters(parameter_snapshot,
                                       centre_longitude=centre.get("longitude"), centre_latitude=centre.get("latitude"))
    stage_snapshot = json.dumps({"mappingVersion": load_reviewed_mapping().mapping_version,
                                 "parameters": parameter_snapshot}, sort_keys=True, separators=(",", ":"))
    raw_main = _input("main", "RAW_INPUT", "MAIN")
    raw_metadata = _input("metadata", "RAW_INPUT", "METADATA")
    raw_browse = _input("browse", "RAW_INPUT", "BROWSE")
    outputs = [
        _output("STANDARD_COG", "standard/czi-l2a.tif", "FINAL"),
        _output("STANDARD_METADATA_JSON", "standard/czi-l2a.json", "FINAL"),
    ]
    features = parameters.algorithms.get("features", {})
    if features.get("enabled"):
        metrics = features.get("metrics", [])
        if any(metric in {"gradient", "local_variance"} for metric in metrics):
            outputs.append(_output("FEATURE_COG", "features/czi-features.tif", "FINAL"))
        outputs.append(_output("FEATURE_METADATA_JSON", "features/czi-features.json", "FINAL"))
    if parameters.algorithms.get("fusion", {}).get("enabled"):
        outputs.extend([
            _output("FUSION_COG", "fusion/czi-rgb.tif", "FINAL"),
            _output("FUSION_PREVIEW_PNG", "fusion/czi-rgb.png", "FINAL"),
        ])
    if parameters.algorithms.get("saliency", {}).get("enabled"):
        outputs.extend([
            _output("SALIENCY_SCORE_COG", "saliency/score.tif", "FINAL"),
            _output("SALIENCY_MASK_COG", "saliency/mask.tif", "FINAL"),
            _output("SALIENCY_PREVIEW_PNG", "saliency/preview.png", "FINAL"),
        ])
    return {"version": "plan-v2", "stages": [
        _stage("STANDARDIZE_L2A", [], [raw_main, raw_metadata, raw_browse], outputs, stage_snapshot)
    ]}


def _stage(key: str, dependencies: list[str], inputs: list[dict[str, Any]], outputs: list[dict[str, Any]], snapshot: str) -> dict[str, Any]:
    return {"stageKey": key, "stageCode": key, "dependsOnStageKeys": dependencies,
            "inputs": inputs, "outputs": outputs, "parameterSnapshot": snapshot}


def _input(key: str, source: str, artifact_type: str) -> dict[str, Any]:
    return {"key": key, "source": source, "artifactType": artifact_type, "required": True}


def _output(artifact_type: str, relative_path: str, retention: str) -> dict[str, Any]:
    return {"artifactType": artifact_type, "relativePath": relative_path, "retention": retention}


def _manifest_paths_by_role(manifest: dict[str, Any]) -> dict[str, list[Path]]:
    values: dict[str, list[Path]] = {}
    for item in manifest.get("inputs", []):
        if not isinstance(item, dict) or not isinstance(item.get("role"), str) or not isinstance(item.get("path"), str):
            raise ValueError("HY-1C CZI L2A manifest inputs are invalid")
        path = Path(item["path"])
        if not path.is_file():
            raise ValueError("HY-1C CZI L2A manifest input file does not exist")
        values.setdefault(item["role"], []).append(path)
    return values


def _single_role_path(paths: dict[str, list[Path]], role: str, extensions: set[str]) -> Path:
    matches = [path for path in paths.get(role, []) if path.suffix.lower() in extensions]
    if len(matches) != 1:
        raise ValueError(f"HY-1C CZI L2A requires one {role} file")
    return matches[0]


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


def _matches_expected_attribute(value: Any, expected: Any) -> bool:
    if value == expected:
        return True
    if isinstance(expected, int) and not isinstance(expected, bool):
        try:
            return float(value) == expected
        except (TypeError, ValueError):
            return False
    return False


def _decode(value: Any) -> Any:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "item"):
        return _decode(value.item())
    return value
