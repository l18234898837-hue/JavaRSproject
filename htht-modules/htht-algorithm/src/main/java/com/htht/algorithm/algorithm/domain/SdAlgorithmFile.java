package com.htht.algorithm.algorithm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 算法文件表
 * </p>
 *
 * @author MSFY
 * @since 2026-07-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
@TableName("sd_algorithm_file")
public class SdAlgorithmFile extends BaseEntity {

    /**
     * 自增主键
     */
    @TableId("id")
    private Long id;

    /**
     * 算法名称
     */
    @TableField("algorithm_name")
    private String algorithmName;

    /**
     * 算法文件路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 算法环境文件路径
     */
    @TableField("env_file_path")
    private String envFilePath;

    /**
     * 算法默认参数（JSON或文本）
     */
    @TableField("default_params")
    private String defaultParams;

    /**
     * 算法可变参数（JSON或文本）
     */
    @TableField("variable_params")
    private String variableParams;

    /**
     * 算法输入文件夹
     */
    @TableField("input_folder")
    private String inputFolder;

    /**
     * 算法输出文件夹
     */
    @TableField("output_folder")
    private String outputFolder;

    /**
     * 算法类型：0-python文件，1-命令行程序
     */
    @TableField("algorithm_type")
    private Short algorithmType;

    /**
     * 算法运行地址（执行目录或部署路径）
     */
    @TableField("run_path")
    private String runPath;
}
