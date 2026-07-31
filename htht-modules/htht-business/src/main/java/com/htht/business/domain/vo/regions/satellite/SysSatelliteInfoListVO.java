package com.htht.business.domain.vo.regions.satellite;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysSatelliteInfoListVO {
    /**
     * 主键
     */
    private Long id;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * 卫星代码
     */
    private String satelliteCode;

    /**
     * 分辨率
     */
    private Double resolution;

    /**
     * 所属国家
     */
    private String country;

    /**
     * NORAD编号
     */
    private String noradNumber;

    /**
     * 布幅宽度
     */
    private Long clothWidth;

    /**
     * 倾斜角度
     */
    private String sideAngle;

    /**
     * 卫星类型
     */
    private String satelliteType;

    /**
     * 轨道高度
     */
    private Integer orbitalAltitude;

    /**
     * 当前高度
     */
    private Integer altitude;

    /**
     * 卫星标识行
     */
    private String satelliteIdentityLine;

    /**
     * 卫星来源
     */
    private String satelliteSource;

    /**
     * 卫星系列
     */
    private String satelliteSeries;

    /**
     * 发射日期
     */
    private LocalDateTime launchDate;

    /**
     * 卫星状态
     */
    private String satelliteStatus;

    /**
     * 描述
     */
    private String remarks;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    /**
     * 类型
     */
    private String satelliteCategory;

}
