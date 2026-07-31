package com.htht.business.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.domain.SysSatelliteTle;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleListVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SysSatelliteTleConverter {
    Page<SysSatelliteTleListVO> entity2page(Page<SysSatelliteTle> page);

    SysSatelliteTleEditVO entity2edit(SysSatelliteTle sysSatelliteTle);

    SysSatelliteTle edit2entity(SysSatelliteTleEditVO editVO);

    List<SysSatelliteTleListVO> entity2list(List<SysSatelliteTle> list);
}
