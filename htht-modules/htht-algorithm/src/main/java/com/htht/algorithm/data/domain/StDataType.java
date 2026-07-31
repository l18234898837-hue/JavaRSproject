package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 存储遥感数据类型定义
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("st_data_type")
public class StDataType implements Serializable {

    /**
     * ID
     */
    @TableId
    private Long id;

    /**
     * 类型显示名称
     */
    @NotBlank(message = "类型显示名称不能为空")
    private String name;

    @NotNull(message = "父级ID不能为空")
    private Long pid;

    private Long productCatalogId;

    private Long calculateCatalogId;

    private int level;

    @TableField(exist = false)
    private List<StDataType> children = new ArrayList<>();
}
