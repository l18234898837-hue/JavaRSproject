import sys


def test_runtime_requires_python_311_or_newer():
    assert sys.version_info >= (3, 11)


def test_worker_package_declares_runtime_minimum():
    from rs_algorithm_worker import MINIMUM_PYTHON

    assert MINIMUM_PYTHON == (3, 11)
