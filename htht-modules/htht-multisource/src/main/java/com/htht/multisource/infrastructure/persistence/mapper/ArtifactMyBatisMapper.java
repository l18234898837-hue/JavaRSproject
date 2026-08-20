package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.ArtifactEntity;

import java.util.List;

public interface ArtifactMyBatisMapper extends BaseMapper<ArtifactEntity> {

    default List<ArtifactEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<ArtifactEntity>()
            .eq(ArtifactEntity::getTaskId, taskId)
            .orderByAsc(ArtifactEntity::getArtifactId));
    }
}
