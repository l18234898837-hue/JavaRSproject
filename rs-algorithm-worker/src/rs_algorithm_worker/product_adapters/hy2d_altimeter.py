from __future__ import annotations

import tarfile

from .base import build_plan, candidate, input_paths


def inspect_manifest(manifest):
    candidates = []
    for path in input_paths(manifest):
        if not path.name.lower().endswith(".tar.gz"):
            raise ValueError("HY-2D altimeter evidence requires tar.gz packages")
        try:
            with tarfile.open(path, "r:gz") as archive:
                members = [member for member in archive.getmembers() if member.isfile()]
                if not members:
                    raise ValueError("HY-2D altimeter evidence is incomplete")
        except (OSError, tarfile.TarError) as error:
            raise ValueError("HY-2D altimeter evidence is unreadable") from error
        candidates.append(candidate(path.stem, "HY-2D-ALTIMETER", "ALONG_TRACK_OBSERVATION", [path],
                                    {"memberCount": len(members)}))
    return candidates
