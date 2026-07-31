package com.htcloud.orbit.vo;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.BoundType;
import lombok.Data;

@Data
public class BoundParam {

    private BoundType type;

    private JSONObject query;

    @Data
    public static class RegionQuery {
        /**
         * 行政区划code
         */
        private String code;
    }

    @Data
    public static class CircleQuery {
        /**
         * 圆心坐标
         */
        private double[] center;
        /**
         * 半径
         */
        private double radius;

        private String wkt;
    }

    @Data
    public static class GeometryQuery {
        /**
         * 矢量wkt字符串
         */
        private String wkt;
    }

}
