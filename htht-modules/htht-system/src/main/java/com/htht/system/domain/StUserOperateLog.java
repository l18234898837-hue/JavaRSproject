package com.htht.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户操作日志表，记录用户的操作行为及结果
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_user_operate_log")
public class StUserOperateLog extends BaseEntity {

    /**
     * 主键，唯一标识每条操作日志
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 执行操作的用户名称
     */
    private String userName;

    /**
     * 操作涉及的功能模块
     */
    private String functionModule;

    /**
     * 操作的类型（如增删改查等）
     */
    private String operateType;

    /**
     * 操作的详细描述信息
     */
    private String description;

    /**
     * 操作的执行结果（成功/失败等状态）
     */
    private String result;
}
