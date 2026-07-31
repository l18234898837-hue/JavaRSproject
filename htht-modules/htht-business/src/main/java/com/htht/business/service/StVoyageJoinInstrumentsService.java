package com.htht.business.service;

import com.htht.business.domain.StVoyageJoinInstruments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-09
 */
public interface StVoyageJoinInstrumentsService extends IService<StVoyageJoinInstruments> {
    TableDataInfo<StVoyageJoinInstruments> selectPage(StVoyageJoinInstruments instruments, PageQuery pageQuery);

}
