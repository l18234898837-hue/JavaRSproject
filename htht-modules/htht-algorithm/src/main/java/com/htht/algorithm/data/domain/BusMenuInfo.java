package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 大屏业务菜单表
 * </p>
 *
 * @author wjx
 * @since 2023-05-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_menu_info")
public class BusMenuInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    @TableId(value = "menu_id")
    private Long menuId;

    /**
     * 菜单名称
     */
    @NotBlank(message = "菜单名称不能为空")
    @Size(min = 0, max = 50, message = "菜单名称长度不能超过{max}个字符")
    private String menuName;

    /**
     * 父级菜单ID
     */
    private Long parentId;
    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 类型（M：目录，C：菜单）
     */
    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    /**
     * 菜单绑定类型（0：算法，1：外链，2：资源，3：组件）
     */
    private String bindType;

    /**
     * 绑定的外链地址
     */
    private String linkUrl;

    private Integer hideTopHeight;

    /**
     * 绑定的算法ID
     */
    private Integer algorithmId;

    /**
     * 资源目录ID
     */
    private Long catalogId;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单状态（0：正常，1：停用）
     */
    private String status;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 是否显示菜单图标
     */
    private Boolean iconShow;

    /**
     * 是否显示下级的菜单图标
     */
    private Boolean iconChildShow;

    /**
     * 备注
     */
    private String remark;

    @TableField(exist = false)
    private List<BusMenuInfo> children;

}
