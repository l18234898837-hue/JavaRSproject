package com.htht.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.page.TableDataInfo;
import com.htht.system.domain.StUserOperateLog;
import com.htht.system.domain.vo.StUserOperateLogPageQueryVo;
import com.htht.system.event.StUserOperateLogEvent;

import java.util.List;

/**
 * <p>
 * 用户操作日志表，记录用户的操作行为及结果 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
public interface StUserOperateLogService extends IService<StUserOperateLog> {
    TableDataInfo<StUserOperateLog> page(StUserOperateLogPageQueryVo vo);

    List<String> functionModule();

    List<String> operateType();

}
