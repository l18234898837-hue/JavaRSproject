package com.htht.business.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/11/4 15:58
 **/
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@Validated
public class QuerySdObserveDataDto {

    /**
     * 卫星的唯一标识ID
     */
    private String sdId;


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
    @NotNull(message = "波长不能为空")
    private Integer  wavelengthStart;
    @NotNull(message = "波长不能为空")
    private Integer  wavelengthEnd;



    //扩展搜索条件
    /**
     * 时间查询参数类型
     *  0、不做日期选择
     *  1、日期时间段查询
     *  2、月份多选查询
     *  3、年份多选查询
     */
    private Integer dateQueryType;

    private LocalDate startDate;

    private LocalDate endDate;

    private List<Integer> monthList;

    private List<Integer> yearList;

    /**
     * 地图查询类型参数
     * 0、不做地图查询
     * 1、区域查询（空间数据查询：地图选择、经纬度、区域文件上传）
     * 2、区域查询（圆形，dataInfo里面是这样子写的，我不知道能不能用）
     * 2、站点查询
     */

    private Integer mapQueryType;

    private String wkt;

    private String radius;

    private List<String> siteList;


    /**
     * 仪器类型多选参数
     */
    private List<String> deviceModeList;


}
