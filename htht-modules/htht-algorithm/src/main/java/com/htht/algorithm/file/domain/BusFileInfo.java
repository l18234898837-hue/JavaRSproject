package com.htht.algorithm.file.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_file_info")
public class BusFileInfo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 文件名称
     */
    private String filename;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 存储路径
     */
    private String path;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 媒体类型
     */
    private String mimeType;

}
