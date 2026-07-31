package com.htcloud.orbit.bis.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrbitForecastSpecDto {

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
     * 轨道高度 单位：米
     */
    @NotBlank(message = "轨道高度不能为空")
    private Integer orbitalAltitude; // 轨道高度 单位：米

    /**
     * 幅宽 单位：米
     */
    @NotBlank(message = "幅宽不能为空")
    private Integer clothWidth; // 幅宽 单位：米

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
     * 预测时间
     */
    private Date forecastTime;

    /**
     * 传感器ID
     */
    private String sensorId; // 传感器ID
    /**
     *  传感器编码
     */
    private String sensorCode; // 传感器编码

    /**
     * 最大侧摆角，单位：度
     */
    private Double maxSideAngle; // 最大侧摆角，单位：度
    /**
     * 最小侧摆角，单位：度
     */
    private Double minSideAngle;// 最小侧摆角，单位：度
    /**
     * 全色分辨率 单位：米
     */
    private String panGsd; // 全色分辨率 单位：米
    /**
     * 多光谱分辨率 单位：米
     */
    private String mulGsd; // 多光谱分辨率 单位：米
    /**
     * 分辨率 单位：米
     */
    private String gsd; // 分辨率 单位：米
    /**
     * 传感器类型
     */
    private String sensorType; // 传感器类型

//    private Boolean existSpec;
//    private String gsdEnumName;
}
