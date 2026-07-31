package com.htht.algorithm.data.tile.grid.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TileInfo {
    /**
     * 最小经度
     */
    private double lngMin;
    /**
     * 最大经度
     */
    private double lngMax;
    /**
     * 最小纬度
     */
    private double latMin;
    /**
     * 最大纬度
     */
    private double latMax;
    /**
     * 切片大小
     */
    private int tileSize;
    private int dataType;
    /**
     * 波段数
     */
    private int bandCount;
    private int offsetX;
    private int offsetY;
    private int bufX;
    private int bufY;

    private int zoom;
    private int row;
    private int col;
    private double[][] bandsData;
}
