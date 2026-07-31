package com.htht.algorithm.algorithm.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import org.springframework.validation.annotation.Validated;

/**
* 算法库与算法文件关联表 查询请求 DTO
*
* @author MSFY
* @since 2026-07-21
*/
@Data
@Validated
@Accessors(chain = true)
public class SdAlgorithmLibraryRelationQuery implements Serializable {

    private static final long serialVersionUID = 1L;

        /**
        * 算法库id（关联sd_algorithm_library.id）
        */
        private Long libraryId;

        /**
        * 算法文件id（关联sd_algorithm_file.id）
        */
        private Long algorithmFileId;

}
