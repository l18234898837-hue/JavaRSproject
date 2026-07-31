package com.htcloud.orbit.vo;

import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Data
public class OrbitForecastCircleRequestVO {
    /**
     * 卫星轨道参数
     */
    @NotNull(message = "卫星轨道参数不能为空")
    private List<OrbitForecastSpecDto> orbitForecastSpecs;
    /**
     * 预测日期时间区间开始
     */
    @NotNull(message = "预测日期时间区间开始不能为空")
    private Date startTime;
    /**
     * 预测日期时间区间结束
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


}
