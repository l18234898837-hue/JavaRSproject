package com.htht.algorithm.data.mapper;

import com.htht.algorithm.data.domain.StAlgorithmRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 算法运行记录表，用于追踪每次算法执行的状态、用户、日志及运行目录 Mapper 接口
 * </p>
 *
 * @author MSFY
 * @since 2025-09-18
 */
public interface StAlgorithmRecordMapper extends BaseMapper<StAlgorithmRecord> {
    long getMaxQueueOrder();
}
