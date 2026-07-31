package com.htht.system.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 *
 * @TableName sys_file
 */
@TableName(value ="sys_file")
@Data
public class SysFile extends BaseEntity implements Serializable {
    /**
     * 主键id
     */
    @TableId
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 扩展名
     */
    private String extension;

    /**
     * 总大小
     */
    private Long totalSize;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 是否静态文件，1是 0否
     */
    private Integer isStatic;

    /**
     * 静态路径
     */
    private String staticPath;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
