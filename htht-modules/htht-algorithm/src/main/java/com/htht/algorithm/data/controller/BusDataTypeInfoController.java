package com.htht.algorithm.data.controller;


import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusDataTypeInfo;
import com.htht.algorithm.data.service.IBusDataTypeInfoService;
import com.htht.common.enums.BusinessType;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 数据类型管理
 *
 * @author wjx
 */
@RestController
@RequestMapping("/algorithm/data-type")
@Validated
@RequiredArgsConstructor
public class BusDataTypeInfoController extends BaseController {

    private final IBusDataTypeInfoService dataTypeInfoService;

    /**
     * 新增数据类型
     *
     * @param dataTypeInfo
     * @return
     */
    @PostMapping
    public R<Void> addDataType(@RequestBody BusDataTypeInfo dataTypeInfo) {
        dataTypeInfoService.addDataType(dataTypeInfo);
        return R.ok();
    }

    /**
     * 删除数据类型
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R<Void> removeDataType(@PathVariable(value = "id") Long id) {
        dataTypeInfoService.removeDataType(id);
        return R.ok();
    }

    /**
     * 分页查询数据类型列表
     *
     * @param query
     * @return
     */
    @GetMapping(value = "page")
    public TableDataInfo<BusDataTypeInfo> selectByPage(BusDataTypeInfo dataTypeInfo, PageQuery query) {
        return dataTypeInfoService.selectDataTypePage(dataTypeInfo, query);
    }

    /**
     * 查询所有的数据类型列表
     *
     * @return
     */
    @GetMapping(value = "list")
    public List<BusDataTypeInfo> selectList() {
        return dataTypeInfoService.selectDataTypeList();
    }

    /**
     * 导出资源类型
     */
    @Log(title = "资源管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BusDataTypeInfo dataTypeInfo, HttpServletResponse response) {
        dataTypeInfoService.export(dataTypeInfo, response);
    }
}
