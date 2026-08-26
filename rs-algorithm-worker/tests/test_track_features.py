from __future__ import annotations

import numpy as np
import pytest

from rs_algorithm_worker.algorithms.track_features import extract_track_features


def test_extract_track_features_reports_sample_index_statistics_per_window() -> None:
    result = extract_track_features(np.array([1.0, 2.0, 3.0, 4.0]), window_size=3)

    assert [(item["startIndex"], item["endIndex"]) for item in result] == [(0, 2), (3, 3)]
    assert [item["sshMean"] for item in result] == [2.0, 4.0]
    assert result[0]["sshStd"] == pytest.approx(np.sqrt(2.0 / 3.0))
    assert result[0]["sshTrend"] == pytest.approx(1.0)
    assert result[0]["sshGradientMean"] == 1.0
    assert result[0]["validRatio"] == 1.0
    assert result[1]["sshStd"] == 0.0
    assert result[1]["sshTrend"] == 0.0
    assert result[1]["sshGradientMean"] == 0.0
    assert result[1]["validRatio"] == pytest.approx(1.0 / 3.0)


def test_extract_track_features_excludes_invalid_values_and_splits_valid_runs() -> None:
    result = extract_track_features(
        np.array([1.0, 3.0, np.nan, 8.0, 100.0]),
        valid_mask=np.array([True, True, True, True, False]),
        window_size=3,
    )

    assert [(item["startIndex"], item["endIndex"]) for item in result] == [(0, 1), (3, 3)]
    assert [item["sshMean"] for item in result] == [2.0, 8.0]
    assert [item["validRatio"] for item in result] == [pytest.approx(2.0 / 3.0), pytest.approx(1.0 / 3.0)]


@pytest.mark.parametrize("window_size", [0, -1, 1.5])
def test_extract_track_features_requires_a_positive_integer_window(window_size: object) -> None:
    with pytest.raises(ValueError, match="window_size"):
        extract_track_features(np.array([1.0]), window_size=window_size)
