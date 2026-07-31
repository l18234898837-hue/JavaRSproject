package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 算法参数信息表
 * </p>
 *
 * @author MSFY
 * @since 2025-10-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_algorithm_param_info")
public class StAlgorithmParamInfo extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId()
    private Long id;

    /**
     * 用户Id
     */
    private Long userid;

    /**
     * 类别1.参数文件(txt),2.区域文件(shp)
     */
    private Integer type;

    /**
     * 关联文件id
     */
    private Long fileId;
}
