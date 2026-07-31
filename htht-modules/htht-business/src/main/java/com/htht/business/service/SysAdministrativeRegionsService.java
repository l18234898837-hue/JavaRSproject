package com.htht.business.service;

import com.htht.business.domain.SysAdministrativeRegions;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsEditVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsListVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsQueryVO;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 行政区域信息表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-25
 */
public interface SysAdministrativeRegionsService extends IService<SysAdministrativeRegions> {

    TableDataInfo<SysAdministrativeRegionsListVO> pageSysAdministrativeRegions(SysAdministrativeRegionsQueryVO queryVO);

    SysAdministrativeRegionsEditVO getSysAdministrativeRegions(Long id);

    int addSysAdministrativeRegions(SysAdministrativeRegionsEditVO editVO);

    int updateSysAdministrativeRegions(SysAdministrativeRegionsEditVO editVO);

    int updateDeleteStatusByIds(List<Long> ids);
}
