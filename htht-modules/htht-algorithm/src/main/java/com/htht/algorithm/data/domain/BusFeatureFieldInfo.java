package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-06-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_feature_field_info")
public class BusFeatureFieldInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    /**
     * 数据ID
     */
    private Long dataId;

    /**
     * 字段名
     */
    private String field;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 字段别名
     */
    private String label;

    /**
     * 是否显示
     */
    private Boolean isShow;

    /**
     * 排序
     */
    private Integer idx;

    /**
     * 单位
     */
    private String unit;

    /**
     * 字段映射
     */
    private String map;


    /**
     * 保留几位小数
     */
    private Integer fixed;
}
