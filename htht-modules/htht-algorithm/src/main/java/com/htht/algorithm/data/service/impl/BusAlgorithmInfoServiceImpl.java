package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.controller.BusAlgorithmInfoController;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.vo.BusAlgorithmVo;
import com.htht.algorithm.data.domain.vo.BusDataTypeVo;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusDataTypeInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;
import com.htht.algorithm.data.mapper.BusAlgorithmInfoMapper;
import com.htht.algorithm.data.service.IBusAlgorithmInfoService;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.common.utils.poi.ExcelUtil;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * 算法信息表 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
@Service
@RequiredArgsConstructor
public class BusAlgorithmInfoServiceImpl extends ServiceImpl<BusAlgorithmInfoMapper, BusAlgorithmInfo> implements IBusAlgorithmInfoService {

    private final TileProperties tileProperties;
    private final IBusDataInfoService dataInfoService;
    private final IBusDataTypeInfoService dataTypeInfoService;
    private final Map<Integer, BusAlgorithmInfo> cacheMap = new ConcurrentHashMap<>();

    @Override
    public void addAlgorithm(BusAlgorithmInfo algorithmInfo) {
        Optional.ofNullable(this.getById(algorithmInfo.getId())).ifPresent((algorithm) -> {
            throw new GlobalException("算法ID已经存在：" + algorithm.getId());
        });
        long count = this.lambdaQuery().eq(BusAlgorithmInfo::getName, algorithmInfo.getName()).or().eq(BusAlgorithmInfo::getAliasName, algorithmInfo.getAliasName()).count();
        if (count > 0) {
            throw new GlobalException("算法名称或别名已经存在，请更换");
        }
        algorithmInfo.setTilePath(tileProperties.getGrid().getTileDir() + File.separator + algorithmInfo.getId());
        this.save(algorithmInfo);
    }

    @Override
    public void removeAlgorithm(Integer id) {
        Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("算法ID不存在:" + id));
        this.removeById(id);
        clearCache(id);
    }

    @Override
    public void updateAlgorithm(BusAlgorithmInfo algorithmInfo) {
        long count = this.lambdaQuery().ne(BusAlgorithmInfo::getId, algorithmInfo.getId()).and(wq -> wq.eq(BusAlgorithmInfo::getName, algorithmInfo.getName()).or().eq(BusAlgorithmInfo::getAliasName, algorithmInfo.getAliasName())).count();
        if (count > 0) {
            throw new GlobalException("该算法名称或别名已经存在，请更换");
        }
        this.updateById(algorithmInfo);
        clearCache(algorithmInfo.getId());
    }

    @Override
    public TableDataInfo<BusAlgorithmInfo> selectByPage(BusAlgorithmInfo algorithmInfo, PageQuery query) {
        return TableDataInfo.build(this.lambdaQuery().eq(Objects.nonNull(algorithmInfo.getId()), BusAlgorithmInfo::getId, algorithmInfo.getId()).like(Strings.isNotBlank(algorithmInfo.getName()), BusAlgorithmInfo::getName, algorithmInfo.getName()).like(Strings.isNotBlank(algorithmInfo.getAliasName()), BusAlgorithmInfo::getAliasName, algorithmInfo.getAliasName()).orderByAsc(BusAlgorithmInfo::getId).page(new Page<>(query.getPageNum(), query.getPageSize())));
    }

    @Override
    public List<BusAlgorithmInfo> selectList() {
        //查询状态正常的算法列表
        return this.lambdaQuery().eq(BusAlgorithmInfo::getStatus, "0").orderByAsc(BusAlgorithmInfo::getId).list();
    }

    @Override
    public BusAlgorithmInfo getAlgorithmInfo(Integer algorithmId) {
        BusAlgorithmInfo algorithmInfo = cacheMap.get(algorithmId);
        if (Objects.isNull(algorithmInfo)) {
            algorithmInfo = Optional.ofNullable(this.getById(algorithmId)).orElseThrow(() -> new GlobalException("算法不存在"));
            cacheMap.put(algorithmId, algorithmInfo);
        }
        return algorithmInfo;
    }

    @Override
    public List<BusAlgorithmInfo> getAlgorithmListByDataId(Long dataId) {
        BusDataInfo dataInfo = dataInfoService.getDataInfoById(dataId);
        return this.lambdaQuery().like(BusAlgorithmInfo::getSupportTypes, dataInfo.getDataType()).list();
    }

    @Override
    public void export(BusAlgorithmInfo algorithmInfo, HttpServletResponse response) {
        List<BusAlgorithmInfo> algorithmInfoList = this.lambdaQuery().eq(Objects.nonNull(algorithmInfo.getId()), BusAlgorithmInfo::getId, algorithmInfo.getId()).like(Strings.isNotBlank(algorithmInfo.getName()), BusAlgorithmInfo::getName, algorithmInfo.getName()).like(Strings.isNotBlank(algorithmInfo.getAliasName()), BusAlgorithmInfo::getAliasName, algorithmInfo.getAliasName()).orderByAsc(BusAlgorithmInfo::getId).list();

        List<BusAlgorithmVo> listVo = BeanUtil.copyToList(algorithmInfoList, BusAlgorithmVo.class);
        listVo.forEach(vo -> {
            vo.setStatus(vo.getStatus().equals("0") ? "停用" : "启用");
        });
        ExcelUtil.exportExcel(listVo, "算法列表", BusAlgorithmVo.class, response);
    }

    private void clearCache(Integer algorithmId) {
        if (cacheMap.containsKey(algorithmId)) {
            cacheMap.remove(algorithmId);
        }
    }
}
