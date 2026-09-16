"""Contract matrix for all supported new-task plan builders."""

from rs_algorithm_worker.product_adapters import (
    fy3h_mersi, fy4b_agri, gf6_wfv_browse, gf6_wfv_l1a, hy1c_czi_l2a, hy2d_altimeter,
)


def _files(roles):
    return [{"role": role, "ordinal": index, "path": f"/input/{role.lower()}-{index}.dat"}
            for index, role in enumerate(roles)]


def test_supported_product_builders_are_plan_v3_and_non_empty():
    cases = [
        (fy3h_mersi.build_staged_plan,
         {"profileKey": {"productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"},
          "dataShape": "SWATH_RASTER", "supportStatus": "SUPPORTED",
          "files": _files(["OBSERVATION", "GEO1K"])} , {}),
        (fy4b_agri.build_staged_plan,
         {"profileKey": {"productCode": "FY-4B-AGRI", "profileVersion": "v1"},
          "dataShape": "GEOSTATIONARY_RASTER", "supportStatus": "SUPPORTED",
          "files": _files(fy4b_agri.ROLE_ORDER)}, {}),
        (gf6_wfv_l1a.build_staged_plan,
         {"profileKey": {"productCode": "GF-6-WFV-L1A", "profileVersion": "v1"},
          "dataShape": "RASTER_GRID", "supportStatus": "SUPPORTED",
          "files": [
              {"role": "MAIN_TIFF_TILE", "ordinal": 0, "path": "/input/GF6_WFV_SAMPLE-1.tif"},
              {"role": "MAIN_TIFF_TILE", "ordinal": 1, "path": "/input/GF6_WFV_SAMPLE-2.tif"},
              {"role": "MAIN_TIFF_TILE", "ordinal": 2, "path": "/input/GF6_WFV_SAMPLE-3.tif"},
              {"role": "METADATA_XML", "ordinal": 0, "path": "/input/GF6_WFV_SAMPLE.xml"},
              {"role": "RPC_RPB", "ordinal": 0, "path": "/input/GF6_WFV_SAMPLE.rpb"},
              {"role": "NAV", "ordinal": 0, "path": "/input/GF6_WFV_SAMPLE.nav"},
              {"role": "TILE_INDEX_TIL", "ordinal": 0, "path": "/input/GF6_WFV_SAMPLE.til"},
          ]}, {}),
        (gf6_wfv_browse.build_staged_plan,
         {"profileKey": {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"}}, gf6_wfv_browse.PUBLIC_PARAMETERS),
        (hy1c_czi_l2a.build_l2a_staged_plan,
         {"profileKey": {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"},
          "metadataSnapshot": {"sceneCentre": {"longitude": 120.0, "latitude": 30.0}}},
         {"schemaVersion": "1", "operation": "STANDARDIZE_L2A", "bands": ["B460", "B560", "B650", "B825"],
          "grid": {"targetCrs": "EPSG:32651", "resolutionMeters": 10, "scienceResampling": "nearest"},
          "quality": {"policy": "default"}, "algorithms": {
              "features": {"enabled": False, "metrics": ["statistics"], "windowSize": 3},
              "fusion": {"enabled": False, "red": "B650", "green": "B560", "blue": "B460", "lowPercentile": 2, "highPercentile": 98},
              "saliency": {"enabled": False, "source": "B650", "threshold": 0.95, "windowSize": 3},
          }}),
        (hy2d_altimeter.build_staged_plan,
         {"profileKey": {"productCode": "HY-2D-ALTIMETER", "profileVersion": "v1"},
          "dataShape": "ALONG_TRACK_OBSERVATION", "supportStatus": "SUPPORTED",
          "files": _files(["MAIN"])}, {}),
    ]

    for builder, candidate, parameters in cases:
        plan = builder(candidate, parameters)
        assert plan["version"] == "plan-v3"
        assert plan["stages"]
        assert all(stage["stageKey"] and stage["stageCode"] for stage in plan["stages"])
