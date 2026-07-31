package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.business.domain.StRadarDataInfo;
import com.htht.business.service.StRadarDataInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 雷达数据信息关联表，用于关联雷达数据与业务场景下的扩展信息 前端控制器
 *
 * @author MSFY
 * @since 2025-09-23
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stRadarDataInfo")
public class StRadarDataInfoController {
    private final StRadarDataInfoService stRadarDataInfoService;
    @SaCheckPermission("radar:data:add")
    @PostMapping()
    public R<String> add(@RequestPart("file")MultipartFile file) {
        stRadarDataInfoService.add(file);
        return R.ok("上传成功");
    }
    @SaCheckPermission("radar:data:page")
    @GetMapping()
    public R<TableDataInfo<StRadarDataInfo>> page( PageQuery pageQuery){
        return R.ok(stRadarDataInfoService.page(pageQuery));
    }

    @SaCheckPermission("radar:data:download")
    @GetMapping("/download/{id}")
    public void download(@PathVariable(value = "id") Long id, HttpServletResponse response) throws IOException {
        stRadarDataInfoService.download(id, response);
    }

}
