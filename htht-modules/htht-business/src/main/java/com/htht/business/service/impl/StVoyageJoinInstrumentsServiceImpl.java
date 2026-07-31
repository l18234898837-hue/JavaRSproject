package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.basic.gdal.image.conversion.AbstractRasterConverter;
import com.basic.gdal.image.conversion.AlgorithmTileConverter;
import com.basic.gdal.image.conversion.ImageIOWriter;
import com.basic.gdal.image.conversion.RasterWriter;
import com.basic.gdal.image.model.ImageInfo;
import com.basic.gdal.image.model.WebTileInfo;
import com.basic.tile.server.starter.componet.HttpServerRunner;
import com.basic.resultcreator.image.color.ColorCreator;
import com.basic.resultcreator.image.color.ColorOptions;
import com.basic.resultcreator.image.color.NormalColorCreator;
import com.htht.business.domain.StVoyageJoinInstruments;
import com.htht.business.mapper.StVoyageJoinInstrumentsMapper;
import com.htht.business.service.StVoyageJoinInstrumentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-09
 */
@Service
public class StVoyageJoinInstrumentsServiceImpl extends ServiceImpl<StVoyageJoinInstrumentsMapper, StVoyageJoinInstruments> implements StVoyageJoinInstrumentsService {

    @Override
    public TableDataInfo<StVoyageJoinInstruments> selectPage(StVoyageJoinInstruments instruments, PageQuery pageQuery) {
        return TableDataInfo.build(lambdaQuery()
            .eq(Objects.nonNull(instruments.getVoyageId()), StVoyageJoinInstruments::getVoyageId, instruments.getVoyageId())
            .eq(Objects.nonNull(instruments.getInstrumentId()), StVoyageJoinInstruments::getInstrumentId, instruments.getInstrumentId())
            .eq(Objects.nonNull(instruments.getDatasetId()), StVoyageJoinInstruments::getDatasetId, instruments.getDatasetId())
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))
        );
    }
}
