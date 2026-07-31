package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StTimerInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

/**
 * <p>
 * 定时器信息表，存储定时任务的配置信息 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
public interface StTimerInfoService extends IService<StTimerInfo> {
    TableDataInfo<StTimerInfo> page(StTimerInfo stTimerInfo, PageQuery pageQuery);

    void checkAdd(StTimerInfo stTimerInfo);

    void TestRunTask(StTimerInfo stTimerInfo);

    void packRunTask(StTimerInfo stTimerInfo);
    void runTask(StTimerInfo stTimerInfo) throws Exception;
}
