package com.htht.algorithm.data.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
public class BusDataInfoVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "数据ID")
    private Long id;

    /**
     * 数据名称
     */
    @ExcelProperty(value = "名称")
    private String name;

    /**
     * 数据类型：0-栅格；1-矢量
     */
    @ExcelProperty(value = "类型")
    private Integer type;

    /**
     * 数据实际时间
     */
    @ExcelProperty(value = "实际时间")
    private Date actualTime;

    /**
     * 数据生产时间
     */
    @ExcelProperty(value = "生产时间")
    private Date productTime;

    /**
     * 描述
     */
    @ExcelProperty(value = "描述")
    private String description;

    /**
     * 资源摘要
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 资源关键词（多个关键词以；分隔）
     */
    @ExcelProperty(value = "关键词")
    private String keywords;

    /**
     * 最小经度
     */
    @ExcelProperty(value = "最小经度")
    private Double lngMin;

    /**
     * 最大经度
     */
    @ExcelProperty(value = "最大经度")
    private Double lngMax;

    /**
     * 最小纬度
     */
    @ExcelProperty(value = "最小纬度")
    private Double latMin;

    /**
     * 最大纬度
     */
    @ExcelProperty(value = "最大经度")
    private Double latMax;

    /**
     * 入库状态（0未入库 1已入库）
     */
    @ExcelProperty(value = "入库状态")
    private String stock;

    /**
     * 状态（0正常 1停用）
     */
    @ExcelProperty(value = "数据状态")
    private String status;


    /**
     * 元数据信息
     */
    @ExcelProperty(value = "元数据信息")
    private String metaData;

    @ExcelProperty(value = "最小层级")
    private Integer minLevel;
    @ExcelProperty(value = "最大层级")
    private Integer maxLevel;
}
