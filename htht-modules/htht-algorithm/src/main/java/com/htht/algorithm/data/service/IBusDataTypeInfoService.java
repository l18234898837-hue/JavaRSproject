package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusDataTypeInfo;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * <p>
 * 数据类型信息表 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
public interface IBusDataTypeInfoService extends IService<BusDataTypeInfo> {

    /**
     * 新增数据类型
     *
     * @param dataTypeInfo
     */
    void addDataType(BusDataTypeInfo dataTypeInfo);

    /**
     * 删除数据类型
     *
     * @param id
     */
    void removeDataType(Long id);

    /**
     * 分页查询数据类型列表
     *
     * @param query
     * @return
     */
    TableDataInfo<BusDataTypeInfo> selectDataTypePage(BusDataTypeInfo dataTypeInfo, PageQuery query);

    /**
     * 查询所有的数据类型列表
     *
     * @return
     */
    List<BusDataTypeInfo> selectDataTypeList();

    /**
     * 资源类型导出
     *
     * @param dataTypeInfo
     */
    void export(BusDataTypeInfo dataTypeInfo, HttpServletResponse response);

}
