package com.htht.algorithm.data.controller;


import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.algorithm.data.domain.BusFeatureFieldInfo;
import com.htht.algorithm.data.service.IBusFeatureFieldInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 要素字段管理
 */
@RestController
@RequestMapping("/algorithm/feature/field")
@RequiredArgsConstructor
@Validated
public class BusFeatureFieldInfoController extends BaseController {

    private final IBusFeatureFieldInfoService featureFieldInfoService;


    /**
     * 根据数据ID查询要素字段列表
     *
     * @param dataId
     * @return
     */
    @GetMapping(value = "/{dataId}")
    public R<List<BusFeatureFieldInfo>> getFieldListByDataId(@PathVariable(value = "dataId") Long dataId) {
        return R.ok(featureFieldInfoService.getFieldListByDataId(dataId));
    }

    /**
     * 更新字段列表
     *
     * @param fieldInfos
     * @return
     */
    @PutMapping
    public R<Void> updateFieldList(@RequestBody List<BusFeatureFieldInfo> fieldInfos) {
        featureFieldInfoService.updateFieldList(fieldInfos);
        return R.ok();
    }

}
