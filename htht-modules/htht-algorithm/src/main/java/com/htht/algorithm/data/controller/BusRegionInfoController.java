package com.htht.algorithm.data.controller;


import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.algorithm.data.domain.BusRegionInfo;
import com.htht.algorithm.data.service.IBusRegionInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行政区划管理
 *
 * @author wjx
 * @since 2023-05-29
 */
@RestController
@RequestMapping("/algorithm/region")
@Validated
@RequiredArgsConstructor
public class BusRegionInfoController extends BaseController {

    private final IBusRegionInfoService regionInfoService;

    /**
     * 构建行政区划树
     *
     * @return
     */
    @GetMapping(value = "/tree")
    public R<List<BusRegionInfo>> buildRegionTree() {
        return R.ok(regionInfoService.buildTree());
    }
}
