package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StTimerLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

/**
 * <p>
 * 定时器运行日志表，记录定时任务每次执行的结果 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
public interface StTimerLogService extends IService<StTimerLog> {
    TableDataInfo<StTimerLog> page(Long userId, Short taskType, String runDate, Integer taskStatus, PageQuery pageQuery);

}
