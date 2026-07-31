package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusFormInfo;
import com.htht.algorithm.data.mapper.BusFormInfoMapper;
import com.htht.algorithm.data.service.IBusFormInfoService;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-07-03
 */
@Service
public class BusFormInfoServiceImpl extends ServiceImpl<BusFormInfoMapper, BusFormInfo> implements IBusFormInfoService {

    @Override
    public int addForm(BusFormInfo formInfo) {
        return baseMapper.insert(formInfo);
    }

    @Override
    public int removeForm(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public int updateForm(BusFormInfo formInfo) {
        return baseMapper.updateById(formInfo);
    }

    @Override
    public TableDataInfo<BusFormInfo> selectByPage(BusFormInfo formInfo, PageQuery query) {
        Page<BusFormInfo> page = this.lambdaQuery()
            .like(Strings.isNotBlank(formInfo.getFormName()), BusFormInfo::getFormName, formInfo.getFormName())
            .page(new Page<>(query.getPageNum(), query.getPageSize()));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BusFormInfo> selectList() {
        return this.list();
    }


}
