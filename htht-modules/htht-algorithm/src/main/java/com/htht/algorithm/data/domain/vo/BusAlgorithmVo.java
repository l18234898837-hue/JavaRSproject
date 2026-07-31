package com.htht.algorithm.data.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class BusAlgorithmVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 算法id（对应docker中的算法ID）
     */
    @ExcelProperty(value = "算法ID")
    private Integer id;

    /**
     * 算法名称
     */
    @ExcelProperty(value = "算法名称")
    private String name;

    /**
     * 算法别名
     */
    @ExcelProperty(value = "算法别名")
    private String aliasName;


    /**
     * 版权
     */
    @ExcelProperty(value = "版权")
    private String copyright;

    /**
     * 发布日期
     */
    @ExcelProperty(value = "发布日期")
    private LocalDateTime deployDate;


    /**
     * 算法状态（0：正常，1：停用）
     */
    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "最小层级")
    private Integer minZoom;
    @ExcelProperty(value = "最大层级")
    private Integer maxZoom;
}
