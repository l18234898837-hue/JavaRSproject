package com.htht.algorithm.algorithm.domain.vo;

import lombok.Data;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;

/**
* 算法文件表 VO
*
* @author MSFY
* @since 2026-07-21
*/
@Data
public class SdAlgorithmFileVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
    * 自增主键
    */
    private Long id;

    /**
    * 算法名称
    */
    private String algorithmName;

    /**
    * 算法文件路径
    */
    private String filePath;

    /**
    * 算法环境文件路径
    */
    private String envFilePath;

    /**
    * 算法默认参数（JSON或文本）
    */
    private String defaultParams;

    /**
    * 算法可变参数（JSON或文本）
    */
    private String variableParams;

    /**
    * 算法输入文件夹
    */
    private String inputFolder;

    /**
    * 算法输出文件夹
    */
    private String outputFolder;

    /**
    * 算法类型：0-python文件，1-命令行程序
    */
    private Short algorithmType;

    /**
    * 算法运行地址（执行目录或部署路径）
    */
    private String runPath;

}
