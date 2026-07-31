package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author MSFY
 * @since 2025-08-29
 */
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@TableName("st_usgs_info")
public class StUsgsInfo  {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 层级
     */
    private Short level;

    /**
     * 备注
     */
    private String remark;

    /**
     * 图层ID
     */
    private String datasatId;

    /**
     * 实例ID
     */
    private String datasatName;

    /**
     * 父级ID
     */
    private Long pid;

    /**
     * 排序
     */
    private Integer orderNum;

    @TableField(exist = false)
    private List<StUsgsInfo> children = new ArrayList<>();
}
