package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.domain.StCalculateCount;
import com.htht.algorithm.data.mapper.StCalculateCountMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.service.StCalculateCountService;
import com.htht.common.exception.GlobalException;
import com.htht.common.helper.LoginHelper;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-14
 */
@Service
public class StCalculateCountServiceImpl extends ServiceImpl<StCalculateCountMapper, StCalculateCount> implements StCalculateCountService {

    @Override
    public void checkCalculate(int count, Long userId) {
        StCalculateCount one = lambdaQuery().eq(StCalculateCount::getUserId, userId).one();
        if (one == null) {
            StCalculateCount calculateCount = new StCalculateCount();
            calculateCount.setUserId(userId);
            calculateCount.setMaxCount(20);
            calculateCount.setCurrentCount(count);
            save(calculateCount);
        }else{
            if (one.getCurrentCount()+count >= one.getMaxCount()) {
                throw new GlobalException("您已超过最大计算次数");
            }
        }
    }

    @Override
    public void setMaxCount(Integer maxCount, Long userId) {
        StCalculateCount one = lambdaQuery().eq(StCalculateCount::getUserId, userId).one();
        if (one == null) {
            StCalculateCount calculateCount = new StCalculateCount();
            calculateCount.setUserId(userId);
            calculateCount.setMaxCount(maxCount);
            calculateCount.setCurrentCount(0);
            save(calculateCount);
        }else{
            one.setMaxCount(maxCount);
            updateById(one);
        }
    }
}
