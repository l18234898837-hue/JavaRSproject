package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * 存储遥感数据入库记录
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_store_data")
public class StStoreData extends BaseEntity {

    /**
     * ID
     */
    @TableId
    private Long id;

    /**
     * 数据描述性名称
     */
    @NotBlank(message = "数据描述性名称不能为空")
    private String name;

    /**
     * 关联data_types表的类型ID
     */
    @NotNull(message = "数据类型ID不能为空")
    private Long typeId;

    /**
     * 文件哈希或唯一标识
     */
    @NotNull(message = "文件哈希或唯一标识不能为空")
    private Long fileId;

    /**
     * 状态：0=禁用,1=未入库,2=已入库，3=入库失败
     */
    private Short status;

    /**
     * 入库错误信息
     */
    private String error;

}
