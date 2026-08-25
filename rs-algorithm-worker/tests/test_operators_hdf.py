import h5py
import numpy as np

from rs_algorithm_worker.operators.hdf import iter_dataset_windows


def test_hdf_window_reader_yields_complete_rows_without_full_dataset_read(tmp_path):
    source = tmp_path / "source.h5"
    with h5py.File(source, "w") as handle:
        handle.create_dataset("science", data=np.arange(15, dtype=np.int16).reshape(5, 3))

    with h5py.File(source, "r") as handle:
        windows = list(iter_dataset_windows(handle["science"], rows_per_window=2))

    assert [window.shape for window in windows] == [(2, 3), (2, 3), (1, 3)]
    np.testing.assert_array_equal(np.concatenate(windows), np.arange(15, dtype=np.int16).reshape(5, 3))
