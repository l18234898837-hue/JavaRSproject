package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.vo.BusDataInfoVo;
import com.htht.algorithm.data.domain.vo.BusDataTypeVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.algorithm.data.domain.BusDataTypeInfo;
import com.htht.algorithm.data.mapper.BusDataTypeInfoMapper;
import com.htht.algorithm.data.service.IBusDataTypeInfoService;
import com.htht.common.utils.poi.ExcelUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * 数据类型信息表 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
@Service
public class BusDataTypeInfoServiceImpl extends ServiceImpl<BusDataTypeInfoMapper, BusDataTypeInfo> implements IBusDataTypeInfoService {

    @Override
    public void addDataType(BusDataTypeInfo dataTypeInfo) {
        long count = this.lambdaQuery()
            .eq(BusDataTypeInfo::getType, dataTypeInfo.getType())
            .or()
            .eq(BusDataTypeInfo::getName, dataTypeInfo.getName())
            .count();
        if (count > 0) {
            throw new GlobalException("数据类型或名称已经存在，请更换");
        }
        this.save(dataTypeInfo);
    }

    @Override
    public void removeDataType(Long id) {
        Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据类型不存在"));
        this.removeById(id);
    }

    @Override
    public TableDataInfo<BusDataTypeInfo> selectDataTypePage(BusDataTypeInfo dataTypeInfo, PageQuery query) {
        return TableDataInfo.build(this.lambdaQuery()
            .like(Strings.isNotBlank(dataTypeInfo.getName()), BusDataTypeInfo::getName, dataTypeInfo.getName())
            .orderByAsc(BusDataTypeInfo::getType)
            .page(new Page<>(query.getPageNum(), query.getPageSize())));
    }

    @Override
    public List<BusDataTypeInfo> selectDataTypeList() {
        return this.lambdaQuery()
            .orderByAsc(BusDataTypeInfo::getType)
            .list();
    }

    @Override
    public void export(BusDataTypeInfo dataTypeInfo, HttpServletResponse response) {
        List<BusDataTypeInfo> dataTypeInfoList = this.lambdaQuery()
            .like(Strings.isNotBlank(dataTypeInfo.getName()), BusDataTypeInfo::getName, dataTypeInfo.getName())
            .orderByAsc(BusDataTypeInfo::getType)
            .list();

        List<BusDataTypeVo> listVo = BeanUtil.copyToList(dataTypeInfoList, BusDataTypeVo.class);
        ExcelUtil.exportExcel(listVo, "资源类型", BusDataTypeVo.class, response);
    }
}
