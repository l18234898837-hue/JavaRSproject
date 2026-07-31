package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.service.StCalculateCountService;
import com.htht.common.core.domain.R;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;

/**
 * 计算总数记录表
 *
 * @author MSFY
 * @since 2025-10-14
 */
@Controller
@RequestMapping("/stCalculateCount")
public class StCalculateCountController {
    private StCalculateCountService stCalculateCountService;
    @SaCheckPermission("update:max:count")
    @PutMapping("setMaxCount")
    public R<Void> setMaxCount(Integer maxCount,Long userId) {
        stCalculateCountService.setMaxCount(maxCount,userId);
        return R.ok();
    }

}
