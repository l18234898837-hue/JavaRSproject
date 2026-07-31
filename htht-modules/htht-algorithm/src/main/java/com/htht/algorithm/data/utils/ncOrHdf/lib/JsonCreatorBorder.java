package com.htht.algorithm.data.utils.ncOrHdf.lib;

import java.util.List;
import java.util.Map;

import com.basic.resultcreator.image.crs.model.LatLng;

import lombok.Data;

/**
 * @author zcsk
 * @date 2025/12/1
 */
@Data
public class JsonCreatorBorder {
    private float minLat;
    private float maxLat;
    private float minLng;
    private float maxLng;

    private float latOffset;
    private float lngOffset;

    private int minX;
    private int maxX;
    private int maxY;
    private int minY;

    private int width;
    private int height;
    private int sizeX;
    private int sizeY;

    /**
     * 类别名称
     */
    private String parameterCategoryName;
    /**
     * 维度名称
     */
    private String parameterNumberName;

    private List<Map<String, Object>> attributes;

    private Map<String, Object> metadata;

    public JsonCreatorBorder() {
    }

    public JsonCreatorBorder(float minLat, float maxLat, float minLng, float maxLng, float degreeOffset, int sizeX, int sizeY) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.latOffset = degreeOffset;
        this.lngOffset = degreeOffset;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
    }

    public JsonCreatorBorder(float minLat, float maxLat, float minLng, float maxLng, float degreeOffset, int sizeX, int sizeY, Map<String, Object> metadata) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.latOffset = degreeOffset;
        this.lngOffset = degreeOffset;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.metadata = metadata;
    }

    public JsonCreatorBorder(float minLat, float maxLat, float minLng, float maxLng, float degreeOffset, int sizeX, int sizeY,
    		String parameterCategoryName, String parameterNumberName, List<Map<String, Object>> attributes) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.latOffset = degreeOffset;
        this.lngOffset = degreeOffset;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.parameterCategoryName = parameterCategoryName;
        this.parameterNumberName = parameterNumberName;
        this.attributes = attributes;
    }

    public JsonCreatorBorder(float minLat, float maxLat, float minLng, float maxLng, float degreeOffset, int sizeX, int sizeY,
    		String parameterCategoryName, String parameterNumberName, Map<String, Object> metadata) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.latOffset = degreeOffset;
        this.lngOffset = degreeOffset;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.parameterCategoryName = parameterCategoryName;
        this.parameterNumberName = parameterNumberName;
        this.metadata = metadata;
    }

    public JsonCreatorBorder(LatLng leftBottom, LatLng rightUpper, Point leftPoint, Point rightPoint) {
        this.setBound(leftBottom,rightUpper,leftPoint,rightPoint);
    }

    public void setBound(LatLng leftBottom, LatLng rightUpper, Point leftPoint, Point rightPoint){
        minY = rightPoint.getY();
        minX = leftPoint.getX();
        maxY = leftPoint.getY();
        maxX = rightPoint.getX();
        width = maxX - minX;
        height = maxY - minY;
        minLat = (float) leftBottom.lat;
        maxLat = (float) rightUpper.lat;
        minLng = (float) leftBottom.lng;
        maxLng = (float) rightUpper.lng;
    }


}
