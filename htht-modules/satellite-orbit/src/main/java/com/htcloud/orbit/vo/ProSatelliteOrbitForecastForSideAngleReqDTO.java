package com.htcloud.orbit.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

@Data
public class ProSatelliteOrbitForecastForSideAngleReqDTO {

    /**
     * 卫星id
     */
    private String satelliteId;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * 轨道高度
     */
    @NotNull(message = "轨道高度不能为空")
    private Integer orbitalAltitude;

    /**
     * 布幅宽度
     */
    @NotNull(message = "布幅宽度不能为空")
    private Integer clothWidth;

    /**
     * TLE参数 第一行
     */
    @NotBlank(message = "TLE参数 第一行不能为空")
    private String tleLine1; // TLE参数 第一行
    /**
     * TLE参数 第二行
     */
    @NotBlank(message = "TLE参数 第二行不能为空")
    private String tleLine2; // TLE参数 第二行

    /**
     * 矢量wkt字符串
     */
    @NotNull(message = "矢量wkt字符串不能为空")
    private String wkt;
//    private BoundParam bound;

    /**
     * 卫星传感器ID
     */
    private String sensorId;

    /**
     * 预测开始时间，格式：yyyy-MM-dd HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @NotNull
    private Date forecastStartTime;

    /**
     * 预测结束时间，格式：yyyy-MM-dd HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @NotNull
    private Date forecastEndTime;

    /**
     * 目标侧摆角
     */
    @NotBlank
    private String sideAngle;

    /**
     * 轨迹位置列表：[[x, y], ...]
     */
    @NotEmpty
    private List<List<Double>> positionList;
}
