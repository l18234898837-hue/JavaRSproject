package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StCalculationResultApplyLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

/**
 * <p>
 * 计算结果上传申请记录表，跟踪计算结果的上传申请及审核流程 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
public interface StCalculationResultApplyLogService extends IService<StCalculationResultApplyLog> {
    TableDataInfo<StCalculationResultApplyLog> page(Long userId, PageQuery pageQuery);

}
