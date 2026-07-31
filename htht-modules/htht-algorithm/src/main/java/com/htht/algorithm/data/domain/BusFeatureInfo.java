package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-5-5
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_feature_info")
public class BusFeatureInfo {

    /**
     * 资源ID
     */
    private Long dataId;
    /**
     * 要素ID
     */
    private Long fId;
    /**
     * 属性json
     */
    private String json;
}
