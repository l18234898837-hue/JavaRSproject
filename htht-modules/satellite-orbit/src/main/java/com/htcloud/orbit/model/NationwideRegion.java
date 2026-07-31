package com.htcloud.orbit.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 全国行政区 不含海域
 *
 * @author ${author}
 * @since 2023-03-08
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "nationwide_region",autoResultMap = true)
public class NationwideRegion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 区划编码
     */
    @TableId(value = "region_code", type = IdType.INPUT)
    private String regionCode;

    /**
     * 区域名称
     */
    @TableField(value = "region_name")
    private String regionName;


    /**
     * 区划级别
     */
    @TableField("region_level")
    private Integer regionLevel;

    /**
     * 父级区划编码
     */
    @TableField("parent_code")
    private String parentCode;

    /**
     * 中心经度
     */
    @TableField("center_longitude")
    private double centerLongitude;

    /**
     * 中心纬度
     */
    @TableField("center_latitude")
    private double centerLatitude;

    /**
     * 排序
     */
    @TableField("region_sort")
    private Integer regionSort;

//    @TableField(typeHandler = GeometryTypeHandler.class)
    private String geom;

    @TableField(exist = false)
    private String geoJson;

    private Date updateTime;
    private Float simplify;
}
