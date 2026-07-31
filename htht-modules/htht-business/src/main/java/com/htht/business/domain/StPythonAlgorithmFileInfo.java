package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * Python算法文件元数据表，关联算法增删改查流程
 * </p>
 *
 * @author MSFY
 * @since 2025-08-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_python_algorithm_file_info")
public class StPythonAlgorithmFileInfo extends BaseEntity {

    /**
     * 主键ID，唯一标识一条Python算法文件记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 算法名称，如“NDVI计算算法”
     */
    private String algorithmName;


    /**
     * Python文件名
     */
    private String pythonFileName;

    /**
     * Python文件路径
     */
    private String fileUrl;

    /**
     * 算法描述，说明该Python算法功能、输入输出要求等
     */
    private String description;
}
