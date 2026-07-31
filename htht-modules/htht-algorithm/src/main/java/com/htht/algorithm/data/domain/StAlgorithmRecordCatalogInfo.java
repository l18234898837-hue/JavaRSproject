package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author MSFY
 * @since 2025-09-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("st_algorithm_record_catalog_info")
public class StAlgorithmRecordCatalogInfo {

    private Long dataTypeId;

    private Long algorithmId;

    private Long dataId;
}
