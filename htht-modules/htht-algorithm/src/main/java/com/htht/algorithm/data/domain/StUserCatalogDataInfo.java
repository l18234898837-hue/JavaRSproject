package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户资源关联表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-10
 */
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@TableName("st_user_catalog_data_info")
public class StUserCatalogDataInfo {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 资源目录ID
     */
    private Long catalogId;

    /**
     * 资源ID
     */
    private Long dataId;
}
