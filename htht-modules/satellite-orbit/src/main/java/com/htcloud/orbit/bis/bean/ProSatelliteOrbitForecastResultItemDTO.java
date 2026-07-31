package com.htcloud.orbit.bis.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.htcloud.orbit.model.ZoneDetectResult;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ProSatelliteOrbitForecastResultItemDTO {


    /**
     * 卫星主键
     */
    private String satelliteId; // ID

    /**
     * 卫星名称
     */
    private String satelliteName; // 名称
    /**
     * 卫星编码
     */
    private String satelliteCode; // 名称
    /**
     * 中文名称
     */
    private String satelliteChineseName; // 中文名称
    /**
     * NORAD编号
     */
    private String noradNumber; // NORAD ID/Catalog number
    /**
     * 国际卫星标识符
     */
    private String satelliteIdentityLine; // 国际卫星标识符 International Designator/NSSDC ID/COSPAR ID
    /**
     * 卫星类型
     */
    private String satelliteType; // 卫星类型（0:SAR;1:光学卫星）
    /**
     * 卫星来源
     */
    private String satelliteSource; // 卫星来源
    /**
     * 卫星系列
     */
    private String satelliteSeries; // 卫星系列

    /**
     * TLE参数 第一行
     */
    private String tleLine1;

    /**
     * TLE参数 第二行
     */
    private String tleLine2;

    /**
     * 轨道高度 单位：米
     */
    private Integer orbitalAltitude;

    private Double minSideAngle;

    private Double maxSideAngle;

    private String sensorId;

    private String sensorCode;

    /**
     * 幅宽 单位：米
     */
    private Integer clothWidth;

    private Double sensorSideAngle;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date entryTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date exitTime;

    private Double geomLength;

    private String geomJson;

    private List<ProSatelliteOrbitForecastStateDto> forecastStateDtos;

    private List<ZoneDetectResult> zoneDetectResultList;

}
