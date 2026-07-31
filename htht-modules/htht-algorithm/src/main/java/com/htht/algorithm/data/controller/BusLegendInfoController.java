package com.htht.algorithm.data.controller;


import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.algorithm.data.domain.BusLegendInfo;
import com.htht.algorithm.data.service.IBusLegendInfoService;
import com.htht.algorithm.data.core.service.impl.GridDataService;
import com.htht.algorithm.data.tile.grid.entity.LegendInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 配色管理
 *
 * @author wjx
 * @since 2023-6-5
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/algorithm/legend")
public class BusLegendInfoController extends BaseController {

    private final IBusLegendInfoService legendInfoService;
    private final GridDataService gridDataService;

    /**
     * 新增配色
     *
     * @param legendInfo
     * @return
     */
    @PostMapping
    public R<Void> addLegend(@RequestBody @Validated BusLegendInfo legendInfo) {
        return toAjax(legendInfoService.addLegend(legendInfo));
    }

    /**
     * 删除配色
     *
     * @param id
     * @return
     */
    @DeleteMapping(value = "/{id}")
    public R<Void> removeLegend(@PathVariable(value = "id") Long id) {
        return toAjax(legendInfoService.removeLegend(id));
    }

    /**
     * 更新配色
     *
     * @param legendInfo
     * @return
     */
    @PutMapping
    public R<Void> updateLegend(@RequestBody @Validated BusLegendInfo legendInfo) {
        return toAjax(legendInfoService.updateLegend(legendInfo));
    }

    /**
     * 根据类型和数据ID或算法ID查询配色列表
     *
     * @param type
     * @param id
     * @return
     */
    @GetMapping(value = "/list")
    public R<List<BusLegendInfo>> getLegendList(int type, Long id) {
        return R.ok(legendInfoService.getLegendList(type, id));
    }

    /**
     * 根据类型和数据ID或算法ID查询启用的配色方案
     *
     * @param type
     * @param id
     * @return
     */
    @GetMapping(value = "/enabled")
    public R<LegendInfo> getEnabledLegend(int type, Long id) {
        return R.ok(gridDataService.cacheLegend(id, type));
    }

    /**
     * 根据类型和数据ID或算法ID查询启用的配色方案
     *
     * @param type
     * @param id
     * @param dataType
     * @return
     */
    @GetMapping(value = "/enabled2")
    public R<LegendInfo> getEnabledLegend2(int type, Long id, String dataType) {
        return R.ok(gridDataService.cacheLegend2(id, type, dataType));
    }

    /**
     * 开启或关闭配色
     *
     * @param id
     * @param enabled
     * @return
     */
    @PutMapping(value = "/enabled")
    public R<Void> enabledLegend(Long id, Boolean enabled) {
        legendInfoService.enabledLegend(id, enabled);
        return R.ok();
    }

    /**
     * 导入sld样式文件
     *
     * @param file
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/sld/import")
    public R<List<Map<String, Object>>> importSld(@RequestPart("file") MultipartFile file) throws Exception {

        return R.ok(legendInfoService.importSld(file));
    }
}
