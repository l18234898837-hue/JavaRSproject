package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.xss.Xss;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_catalog_info")
public class BusCatalogInfo {

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 父级ID
     */
    @NotNull(message = "父级ID不能为空")
    private Long pId;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 名称
     */
    @Xss(message = "数据名称不能包含脚本字符")
    @NotBlank(message = "数据名称不能为空")
    @Size(min = 0, max = 50, message = "用户账号长度不能超过{max}个字符")
    private String name;

    /**
     * 类型：0-普通目录；1-资源目录
     */
    private Integer type;

    /**
     * 图标
     */
    private String icon;

    private String dataType;

    private String remark;

    @TableField(exist = false)
    private List<BusCatalogInfo> children = new ArrayList<>();
}
