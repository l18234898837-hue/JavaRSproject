package com.htht.business.service;

import com.htht.business.domain.SdObserveData;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.business.domain.dto.QuerySdObserveDataDto;
import com.htht.business.domain.vo.SdObserveDataVo;
import com.htht.business.domain.vo.SdObserveIntervalVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.List;

/**
 * <p>
 * 卫星观测的详细数据记录表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
public interface SdObserveDataService extends IService<SdObserveData> {

    TableDataInfo<SdObserveDataVo> query(QuerySdObserveDataDto query, PageQuery pageQuery);

    List<String> querySite();

    List<String> queryDevice();

    SdObserveIntervalVo getInterval();
}
