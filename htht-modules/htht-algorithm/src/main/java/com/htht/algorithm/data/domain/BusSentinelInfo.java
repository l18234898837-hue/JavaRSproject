package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-07-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_sentinel_info")
public class BusSentinelInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.NONE)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 备注
     */
    private String remark;

    /**
     * 图层ID
     */
    private String layerId;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 父级ID
     */
    private Long pid;

    /**
     * 排序
     */
    private Integer orderNum;

    /**
     * api接口前缀
     */
    private String api;

    /**
     * 是否有云盖量
     */
    private Boolean hasCloud;

    /**
     * 数据集
     */
    private String collection;

    /**
     * 过滤器
     */
    private String filter;

    /**
     * 切片格式
     */
    private String imageType;

    private Integer dataType;

    @TableField(exist = false)
    private List<BusSentinelInfo> children;

}
