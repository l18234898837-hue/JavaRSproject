package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-06-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_legend_info")
public class BusLegendInfo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 数据ID
     */
    private Long dataId;

    /**
     * 图例（采用json格式存储）
     */
    private String legend;

    /**
     * 类型（1-数据，2-算法）
     */
    @NotNull(message = "类型不能为空")
    private Integer type;

    /**
     * 算法ID
     */
    private Long algorithmId;

    /**
     * 配色类型：1-波段组合，2-唯一值，3-分级
     */
    @NotNull(message = "配色类型不能为空")
    private Integer legendType;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 名称
     */
    @NotBlank(message = "配色名称不能为空")
    private String name;

    /**
     * 数据类型
     */
    private String dataType;

}
