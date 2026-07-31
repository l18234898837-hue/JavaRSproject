package com.htht.business.service;

import com.htht.business.domain.SysSatelliteInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.business.domain.vo.DictVO;
import com.htht.business.domain.vo.regions.satellite.SatelliteTreeNode;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoQueryVO;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 卫星信息表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-26
 */
public interface SysSatelliteInfoService extends IService<SysSatelliteInfo> {

    TableDataInfo<SysSatelliteInfoListVO> pageSysSatelliteInfo(SysSatelliteInfoQueryVO queryVO);

    SysSatelliteInfoEditVO getSysSatelliteInfo(Long id);

    int addSysSatelliteInfo(SysSatelliteInfoEditVO editVO);

    int updateSysSatelliteInfo(SysSatelliteInfoEditVO editVO);

    int updateDeleteStatusByIds(List<Long> ids);

    List<SatelliteTreeNode> treeSysSatelliteInfo();

    TableDataInfo<DictVO> dictSysSatelliteInfo();
}
