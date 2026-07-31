package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.xss.Xss;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 算法信息表
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_algorithm_info")
public class BusAlgorithmInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 算法id（对应docker中的算法ID）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /**
     * 算法名称
     */
    @Xss(message = "算法名称不能包含脚本字符")
    @NotBlank(message = "算法名称不能为空")
    private String name;

    /**
     * 算法别名
     */
    @Xss(message = "算法别名不能包含脚本字符")
    @NotBlank(message = "算法别名不能为空")
    private String aliasName;

    /**
     * 支持的数据类型（多个类型以；分割）
     */
    private String supportTypes;

    /**
     * 版权
     */
    private String copyright;

    /**
     * 发布日期
     */
    @NotNull(message = "发布日期不能为空")
    private LocalDateTime deployDate;

    /**
     * 图标
     */
    private String icon;

    /**
     * 数据数量
     */
    private Integer count;

    /**
     * 比对数据1关键词
     */
    private String keyWord1;

    /**
     * 比对数据2关键词
     */
    private String keyWord2;

    /**
     * 算法状态（0：正常，1：停用）
     */
    private String status;

    /**
     * 切片缓存地址
     */
    private String tilePath;

    /**
     * 表单ID
     */
    private Long formId;

    private String ip;
    private Integer port;

    private Integer minZoom;
    private Integer maxZoom;
}
