package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 雷达数据信息关联表，用于关联雷达数据与业务场景下的扩展信息
 * </p>
 *
 * @author MSFY
 * @since 2025-09-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_radar_data_info")
public class StRadarDataInfo extends BaseEntity {

    /**
     * 自增主键，唯一标识关联记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务侧对雷达数据的命名（便于业务层识别）
     */
    private String dataName;

    /**
     * 管理文件表id，建立数据关联关系
     */
    private Long fileId;
}
