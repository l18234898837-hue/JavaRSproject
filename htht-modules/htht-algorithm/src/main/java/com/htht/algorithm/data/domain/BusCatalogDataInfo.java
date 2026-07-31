package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 * 目录资源关联表
 * </p>
 *
 * @author wjx
 * @since 2023-05-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_catalog_data_info")
public class BusCatalogDataInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目录ID
     */
    @TableField(value = "catalog_id")
    private Long catalogId;

    /**
     * 资源ID
     */
    @TableField("data_id")
    private Long dataId;


}
