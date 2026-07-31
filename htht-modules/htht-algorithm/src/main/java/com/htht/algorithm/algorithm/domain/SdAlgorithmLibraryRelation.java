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
 * 算法库与算法文件关联表
 * </p>
 *
 * @author MSFY
 * @since 2026-07-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
@TableName("sd_algorithm_library_relation")
public class SdAlgorithmLibraryRelation extends BaseEntity {

    /**
     * 自增主键
     */
    @TableId("id")
    private Long id;

    /**
     * 算法库id（关联sd_algorithm_library.id）
     */
    @TableField("library_id")
    private Long libraryId;

    /**
     * 算法文件id（关联sd_algorithm_file.id）
     */
    @TableField("algorithm_file_id")
    private Long algorithmFileId;
}
