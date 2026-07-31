package com.htht.system.controller.system;

import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.system.service.SysInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/info")
public class SysInfoController extends BaseController {

    private final SysInfoService sysInfoService;

    /**
     * 获取系统信息
     */
    @GetMapping("/getSysInfo")
    public R<Map<String, Object>> list() {
        return R.ok(sysInfoService.getSysInfo());
    }

}
