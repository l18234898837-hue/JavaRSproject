package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * <p>
 * 算法信息表 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
public interface IBusAlgorithmInfoService extends IService<BusAlgorithmInfo> {

    /**
     * 新增算法
     *
     * @param algorithmInfo
     */
    void addAlgorithm(BusAlgorithmInfo algorithmInfo);

    /**
     * 删除算法
     *
     * @param id
     */
    void removeAlgorithm(Integer id);

    /**
     * 修改算法
     *
     * @param algorithmInfo
     */
    void updateAlgorithm(BusAlgorithmInfo algorithmInfo);

    /**
     * 分页查询算法列表
     *
     * @param algorithmInfo
     * @param query
     * @return
     */
    TableDataInfo<BusAlgorithmInfo> selectByPage(BusAlgorithmInfo algorithmInfo, PageQuery query);

    /**
     * 查询所有的算法列表
     *
     * @return
     */
    List<BusAlgorithmInfo> selectList();

    BusAlgorithmInfo getAlgorithmInfo(Integer algorithmId);

    List<BusAlgorithmInfo> getAlgorithmListByDataId(Long dataId);

    void export(BusAlgorithmInfo algorithmInfo, HttpServletResponse response);

}
