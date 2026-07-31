package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StCalculateCount;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-14
 */
public interface StCalculateCountService extends IService<StCalculateCount> {
    void checkCalculate(int count, Long userId);

    void setMaxCount(Integer maxCount, Long userId);
}
