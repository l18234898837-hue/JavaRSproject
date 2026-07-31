package com.htht.business.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/3/19 15:19
 **/
@Data
public class SdObserveIntervalVo {
    /**
     * 观测点的水深
     */
    private BigDecimal waterDepthStart;
    //水深结束
    private BigDecimal waterDepthEnd;


    /**
     * 悬浮颗粒物浓度（单位：mg/L）
     */
    private BigDecimal  spmStart;
    private BigDecimal  spmEnd;

    /**
     * 无机颗粒物浓度（单位：g/L）
     */
    private BigDecimal  pimStart;
    private BigDecimal  pimEnd;

    /**
     * 有机颗粒物浓度（单位：g/L）
     */
    private BigDecimal  pomStart;
    private BigDecimal  pomEnd;

    /**
     * 无机颗粒物占比（单位：%）
     */
    private BigDecimal  pimPerStart;
    private BigDecimal  pimPerEnd;

    /**
     * 有机颗粒物占比（单位：%）
     */
    private BigDecimal  pomPerStart;
    private BigDecimal  pomPerEnd;

    /**
     * 叶绿素a浓度（单位：ug/L）
     */
    private BigDecimal  chlaStart;
    private BigDecimal  chlaEnd;

    /**
     * 海表温度（SST）
     */
    private BigDecimal  sstStart;
    private BigDecimal  sstEnd;

    /**
     * 盐度（Salinity）
     */
    private BigDecimal  salinityStart;
    private BigDecimal  salinityEnd;


    private BigDecimal tsmStart;
    private BigDecimal tsmEnd;

    private BigDecimal sshStart;
    private BigDecimal sshEnd;

    private BigDecimal windStart;
    private BigDecimal windEnd;

    private BigDecimal currentStart;
    private BigDecimal currentEnd;
    /**
     * ag(400)光学参数值
     */
    private BigDecimal  agStart;
    private BigDecimal  agEnd;

    /**
     * s光学参数值（单位：1/nm）
     */
    private BigDecimal  sStart;
    private BigDecimal  sEnd;

    /**
     * 有色可溶性有机物（CDOM）相关数据
     */
    private String cdomStart;
    private String cdomEnd;

    /**
     * 波长
     */
    private Integer  wavelengthStart;
    private Integer  wavelengthEnd;
}
