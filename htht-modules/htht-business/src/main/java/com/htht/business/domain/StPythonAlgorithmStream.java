package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * Python算法执行流程表，记录算法组、执行顺序及关联的Python文件
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_python_algorithm_stream")
public class StPythonAlgorithmStream extends BaseEntity {

    /**
     * 主键ID，唯一标识一条执行流程记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 算法组名称（用于归类，如“影像预处理组”“特征提取组”）
     */
    private String algorithmGroup;

    /**
     * 同一算法组内的执行顺序（数值越小越先执行）
     */
    private Integer executionOrder;

    /**
     * python算法文件id
     */
    private Long pythonInfoId;
}
