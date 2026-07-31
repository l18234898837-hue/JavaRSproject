package com.htht.algorithm.data.domain.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class BusDataTypeVo implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 主键ID
     */
    @ExcelProperty(value = "类型ID")
    private Long id;

    /**
     * 数据类型
     */
    @ExcelProperty(value = "数据类型")
    private String type;

    /**
     * 类型名称
     */
    @ExcelProperty(value = "类型名称")
    private String name;

}
