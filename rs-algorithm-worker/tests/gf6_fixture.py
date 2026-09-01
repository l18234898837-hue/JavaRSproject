from __future__ import annotations

from pathlib import Path

import numpy as np
import rasterio
from rasterio.transform import from_origin


def make_gf6_package(root: Path) -> dict:
    root.mkdir(parents=True, exist_ok=True)
    base = "GF6_WFV_SAMPLE"
    paths = []
    for ordinal, offset in enumerate((0, 2, 4), start=1):
        path = root / f"{base}-{ordinal}.tif"
        with rasterio.open(path, "w", driver="GTiff", width=2, height=2, count=8,
                           dtype="uint16", transform=from_origin(100, 10, 1, 1)) as dataset:
            dataset.write(np.full((8, 2, 2), ordinal, dtype=np.uint16))
        paths.append(path)
    (root / f"{base}.xml").write_text("""<ProductMetaData>
<SatelliteID>GF6</SatelliteID><SensorID>WFV</SensorID><ProductLevel>LEVEL1A</ProductLevel>
<ProductID>SAMPLE</ProductID><SceneID>SCENE</SceneID><OrbitID>1</OrbitID><ImageGSD>16</ImageGSD>
<WidthInPixels>6</WidthInPixels><HeightInPixels>2</HeightInPixels>
<AGain>1,1,1,1,1,1,1,1</AGain><AOffset>0,0,0,0,0,0,0,0</AOffset>
<TopLeftLongitude>100</TopLeftLongitude><TopLeftLatitude>10</TopLeftLatitude>
<TopRightLongitude>101</TopRightLongitude><TopRightLatitude>10</TopRightLatitude>
<BottomRightLongitude>101</BottomRightLongitude><BottomRightLatitude>9</BottomRightLatitude>
<BottomLeftLongitude>100</BottomLeftLongitude><BottomLeftLatitude>9</BottomLeftLatitude>
</ProductMetaData>""", encoding="utf-8")
    denominator = "(" + ",".join(["1"] + ["0"] * 19) + ")"
    line_numerator = "(" + ",".join(["0", "1"] + ["0"] * 18) + ")"
    sample_numerator = "(" + ",".join(["0", "0", "1"] + ["0"] * 17) + ")"
    (root / f"{base}.rpb").write_text("\n".join([
        "lineOffset = 1;", "sampOffset = 1;", "latOffset = 10;", "longOffset = 100;",
        "heightOffset = 0;", "lineScale = 1;", "sampScale = 1;", "latScale = 1;",
        "longScale = 1;", "heightScale = 1;", f"lineNumCoef = {line_numerator};",
        f"lineDenCoef = {denominator};", f"sampNumCoef = {sample_numerator};", f"sampDenCoef = {denominator};",
    ]), encoding="utf-8")
    (root / f"{base}.nav").write_text("navigation", encoding="utf-8")
    til = []
    for ordinal, offset in enumerate((0, 2, 4), start=1):
        til.append(f'BEGIN_GROUP = TILE\nfilename = "{base}-{ordinal}.tif"\nULColOffset = {offset}\nULRowOffset = 0\nEND_GROUP = TILE')
    (root / f"{base}.til").write_text("\n".join(til), encoding="utf-8")
    return {"inputs": [{"path": str(path)} for path in sorted(root.iterdir())]}
