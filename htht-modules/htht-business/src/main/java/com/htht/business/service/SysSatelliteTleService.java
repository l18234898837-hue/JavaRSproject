package com.htht.business.service;

import com.htht.business.domain.SysSatelliteTle;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleQueryVO;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 轨道信息表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-28
 */
public interface SysSatelliteTleService extends IService<SysSatelliteTle> {

    TableDataInfo<SysSatelliteTleListVO> pageSysSatelliteTle(SysSatelliteTleQueryVO queryVO);

    SysSatelliteTleEditVO getSysSatelliteTle(Long id);

    int addSysSatelliteTle(SysSatelliteTleEditVO editVO);

    int updateSysSatelliteTle(SysSatelliteTleEditVO editVO);

    int updateDeleteStatusByIds(List<Long> ids);

    TableDataInfo<SysSatelliteTleListVO> listSysSatelliteTle(List<Long> satelliteInfoIds);
}
