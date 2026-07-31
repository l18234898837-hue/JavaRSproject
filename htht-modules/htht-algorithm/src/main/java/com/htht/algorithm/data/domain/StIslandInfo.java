package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.algorithm.data.convert.GeometryTypeConvert;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 存储群岛信息，含层级关系（parent_id和level）
 * </p>
 *
 * @author MSFY
 * @since 2025-08-18
 */
@Data
@Accessors(chain = true)
@TableName("st_island_info")
@EqualsAndHashCode(callSuper = true)
public class StIslandInfo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键，唯一标识每条记录
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 群岛名称（如东沙、西沙）
     */
    private String archipelago;

    /**
     * 标准名称（拼音/混合格式）
     */
    private String standardName;

    /**
     * 中文地理名称
     */
    private String chineseName;

    /**
     * 北纬坐标（处理度分，转成计算机程序使用的值来处理）
     */
    private String latitude;

    /**
     * 东经坐标（处理度分，转成计算机程序使用的值来处理）
     */
    private String longitude;

    /**
     * 父级ID，关联本表id，顶级节点可为NULL
     */
    private Long parentId;

    /**
     * 层级级别（如1=顶级，2=子级，3=孙级等）
     */
    private Integer level;


    /**
     * 矢量
     */
    @TableField(typeHandler = GeometryTypeConvert.class)
    private String geom;

    private String delFlag;

    @TableField(exist = false)
    private List<StIslandInfo> children = new ArrayList<>();
}
