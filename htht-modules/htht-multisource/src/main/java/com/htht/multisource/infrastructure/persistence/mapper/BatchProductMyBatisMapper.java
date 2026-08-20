package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.BatchProductEntity;

import java.util.List;

public interface BatchProductMyBatisMapper extends BaseMapper<BatchProductEntity> {

    default List<BatchProductEntity> selectByBatchId(long batchId) {
        return selectList(new LambdaQueryWrapper<BatchProductEntity>().eq(BatchProductEntity::getBatchId, batchId));
    }
}
