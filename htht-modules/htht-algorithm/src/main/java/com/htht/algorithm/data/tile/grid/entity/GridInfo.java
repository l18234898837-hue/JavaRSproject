package com.htht.algorithm.data.tile.grid.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 栅格数据信息实体类
 *
 * @author wjx
 * @date 2023-4-19
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class GridInfo {

    private String path;
    private String proj;
    private double[] geoTransform;
    private int bandCount;
    private int dataType;
    private int xSize;
    private int ySize;
    private double xMin;
    private double xMax;
    private double yMin;
    private double yMax;
    private double lngMin;
    private double lngMax;
    private double latMin;
    private double latMax;
    private double srcWePixelResolution;
    private double srcNsPixelResolution;

    private double minValue;
    private double maxValue;
}
