package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.algorithm.data.convert.GeometryTypeConvert;
import com.htht.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 卫星观测的详细数据记录表
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sd_observe_data")
public class SdObserveData extends BaseEntity {

    /**
     * 自增主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 卫星的唯一标识ID
     */
    private String sdId;

    /**
     * 观测站点名称或标识
     */
    private String site;

    /**
     * 观测日期
     */
    private LocalDate date;

    /**
     * 观测时间
     */
    private LocalTime time;

    /**
     * 观测点的水深
     */
    private BigDecimal waterDepth;

    /**
     * 观测点的经度坐标
     */
    private BigDecimal longitude;

    /**
     * 观测点的纬度坐标
     */
    private BigDecimal  latitude;

    /**
     * 悬浮颗粒物浓度（单位：mg/L）
     */
    private BigDecimal  spm;

    /**
     * 无机颗粒物浓度（单位：g/L）
     */
    private BigDecimal  pim;

    /**
     * 有机颗粒物浓度（单位：g/L）
     */
    private BigDecimal  pom;

    /**
     * 无机颗粒物占比（单位：%）
     */
    private BigDecimal  pimPer;

    /**
     * 有机颗粒物占比（单位：%）
     */
    private BigDecimal  pomPer;

    /**
     * 叶绿素a浓度（单位：ug/L）
     */
    private BigDecimal  chla;

    /**
     * 海表温度（SST）
     */
    private BigDecimal  sst;

    /**
     * 盐度（Salinity）
     */
    private BigDecimal  salinity;


    private BigDecimal tsm;

    private BigDecimal ssh;

    private BigDecimal wind;

    private BigDecimal current;

    /**
     * ag(400)光学参数值
     */
    private BigDecimal  ag;

    /**
     * s光学参数值（单位：1/nm）
     */
    private BigDecimal  s;

    /**
     * 有色可溶性有机物（CDOM）相关数据
     */
    private String cdom;

    /**
     * 用于观测的设备型号
     */
    private String deviceModel;

    @TableField(typeHandler = GeometryTypeConvert.class)
    private String geom;
}
