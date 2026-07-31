package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusFormInfo;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-07-03
 */
public interface IBusFormInfoService extends IService<BusFormInfo> {

    /**
     * 新增表单
     *
     * @param formInfo
     * @return
     */
    int addForm(BusFormInfo formInfo);

    /**
     * 删除表单
     *
     * @param id
     * @return
     */
    int removeForm(Long id);

    /**
     * 更新表单
     *
     * @param formInfo
     * @return
     */
    int updateForm(BusFormInfo formInfo);

    /**
     * 分页查询表单
     *
     * @param formInfo
     * @param query
     * @return
     */
    TableDataInfo<BusFormInfo> selectByPage(BusFormInfo formInfo, PageQuery query);

    /**
     * 查询所有的表单
     * @return
     */
    List<BusFormInfo> selectList();
}
