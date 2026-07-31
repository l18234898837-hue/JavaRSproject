package com.htht.algorithm.algorithm.domain.vo;

import lombok.Data;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;

/**
* 算法库与算法文件关联表 VO
*
* @author MSFY
* @since 2026-07-21
*/
@Data
public class SdAlgorithmLibraryRelationVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
    * 自增主键
    */
    private Long id;

    /**
    * 算法库id（关联sd_algorithm_library.id）
    */
    private Long libraryId;

    /**
    * 算法文件id（关联sd_algorithm_file.id）
    */
    private Long algorithmFileId;

}
