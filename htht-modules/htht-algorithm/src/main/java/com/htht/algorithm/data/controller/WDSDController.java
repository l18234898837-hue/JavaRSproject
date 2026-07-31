package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.vo.WDSDQuery;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.SdWindFileIndexService;
import com.htht.algorithm.data.service.WDSDService;
import com.htht.common.core.domain.R;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import me.msfy.windsd.domain.WindData;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 *  海洋洋流展示获取对应数据序列工具
 *
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/18 17:55
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/WDSD")
public class WDSDController {
    private final SdWindFileIndexService sdWindFileIndexService;

    @PostMapping("/getData")
    public R<List<WindData>> getData(@RequestBody WDSDQuery query) throws Exception {
        sdWindFileIndexService.checkData( query);
        return R.ok(sdWindFileIndexService.genData( query));

    }


}
