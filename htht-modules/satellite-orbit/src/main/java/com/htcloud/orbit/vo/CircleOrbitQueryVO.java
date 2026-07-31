package com.htcloud.orbit.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class CircleOrbitQueryVO {

    /**
     *  tle line 1
     */
    @NotBlank(message = "tleLine1不能为空")
    private String tleLine1;
    /**
     *  tle line 2
     */
    @NotBlank(message = "tleLine2不能为空")
    private String tleLine2;
    /**
     *  预测日期时间区间开始
     */
    @NotNull(message = "预测日期时间区间开始不能为空")
    private Date startTime;
    /**
     *  预测日期时间区间结束
     */
    @NotNull(message = "预测日期时间区间结束不能为空")
    private Date endTime;

    /**
     * 圆心经度
     */
    @NotNull(message = "圆心经度不能为空")
    private double centerLon;

    /**
     * 圆心纬度
     */
    @NotNull(message = "圆心纬度不能为空")
    private double centerLat;

    /**
     * 圆半径(单位：千米)
     */
    @NotNull(message = "圆半径不能为空")
    private double radiusKm;

    /**
     * 多边形边数(越多越接近圆形)
     */
    @NotNull(message = "多边形边数不能为空")
    private int segments;

    /**
     * 卫星ID
     */
    private Long satelliteInfoId;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * NORAD编号
     */
    private String noradNumber;

}
