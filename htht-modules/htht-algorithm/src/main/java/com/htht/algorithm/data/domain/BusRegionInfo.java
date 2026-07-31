package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.htht.algorithm.data.convert.GeometryTypeConvert;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_region_info")
public class BusRegionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 编码
     */
    private String code;

    /**
     * 父级编码
     */
    private String pCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 矢量
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField(typeHandler = GeometryTypeConvert.class)
    private String geometry;

    /**
     * 等级（1：省，2：市）
     */
    private Integer level;

    @TableField(exist = false)
    private List<BusRegionInfo> children = new ArrayList<>();

}
