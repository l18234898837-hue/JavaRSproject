package com.htht.algorithm.data.tile.vector.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.gdal.ogr.Layer;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.util.Map;

/**
 * shp文件信息类
 *
 * @author wjx
 * @since 2023-4-28
 */
@Data
@AllArgsConstructor
public class ShpInfo {


    /**
     * 图层名称
     */
    private String layerName;

    /**
     * 空间参考坐标系
     */
    private SpatialReference spatialReference;

    /**
     * 最小x坐标
     */
    private double xMin;
    /**
     * 最小y坐标
     */
    private double yMin;
    /**
     * 最大x坐标
     */
    private double xMax;
    /**
     * 最大y坐标
     */
    private double yMax;


    /**
     * 最小经度
     */
    private double lngMin;
    /**
     * 最小纬度
     */
    private double latMin;
    /**
     * 最大经度
     */
    private double lngMax;
    /**
     * 最大纬度
     */
    private double latMax;

    /**
     * 图层要素个数
     */
    private long featureCount;

    /**
     * 字段数
     */
    private int fieldCount;

    /**
     * 属性字段（属性名称：字段类型）
     */
    private Map<String, String> fieldMap;

    /**
     * 图层
     */
    private Layer layer;

    /**
     * 经纬度转投影坐标转换器
     */
    private CoordinateTransformation wgs842ProjTransformation;

    /**
     * 投影坐标转经纬度转换器
     */
    private CoordinateTransformation proj2Wgs84Transformation;

}
