package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.dtflys.forest.annotation.Get;
import com.htht.business.domain.StPythonAlgorithmFileInfo;
import com.htht.business.domain.vo.PythonAlgorithmFileInfoUpdateVo;
import com.htht.business.service.StPythonAlgorithmFileInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Python算法文件元数据表，关联算法增删改查流程 前端控制器
 *
 * @author MSFY
 * @since 2025-08-14
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stPythonAlgorithmFileInfo")
public class StPythonAlgorithmFileInfoController {
    private final StPythonAlgorithmFileInfoService stPythonAlgorithmFileInfoService;

    @PostMapping
    public R<Void> save(
        @RequestPart("file") MultipartFile file,
        @RequestPart("algorithmName")  String algorithmName,
        @RequestPart("description") String description){
        return stPythonAlgorithmFileInfoService.add(file, algorithmName, description)?R.ok():R.fail();
    }
    @PutMapping
    public R<Void> update(PythonAlgorithmFileInfoUpdateVo pAFU) {
        return stPythonAlgorithmFileInfoService.updateById(
            new StPythonAlgorithmFileInfo()
                .setId(pAFU.getId())
                .setDescription(pAFU.getDescription())
                .setAlgorithmName(pAFU.getAlgorithmName())
        ) ? R.ok():R.fail();
    }

    /**
     * 传入文件跟id,自动处理更新逻辑
     */
    @PutMapping("/file/{id}")
    public String update(@RequestPart("file") MultipartFile file, @PathVariable Long id) {
        return stPythonAlgorithmFileInfoService.updateFile(file, id) ? "更新成功" : "更新失败";
    }
    @GetMapping("/list")
    public List<StPythonAlgorithmFileInfo> list(StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo) {
        LambdaQueryWrapper<StPythonAlgorithmFileInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ObjectUtils.isNotNull(stPythonAlgorithmFileInfo.getId()),StPythonAlgorithmFileInfo::getId, stPythonAlgorithmFileInfo.getId())
            .like(ObjectUtils.isNotNull(stPythonAlgorithmFileInfo.getAlgorithmName()),StPythonAlgorithmFileInfo::getAlgorithmName, stPythonAlgorithmFileInfo.getAlgorithmName());
        return stPythonAlgorithmFileInfoService.list(queryWrapper);
    }

    @GetMapping("/page")
    public TableDataInfo<StPythonAlgorithmFileInfo> page(StPythonAlgorithmFileInfo stPythonAlgorithmFileInfo, PageQuery pageQuery) {
        return stPythonAlgorithmFileInfoService.page(stPythonAlgorithmFileInfo,pageQuery);
    }

    @GetMapping("/{id}")
    public StPythonAlgorithmFileInfo getById(@PathVariable Long id) {
        return stPythonAlgorithmFileInfoService.getById(id);
    }

    @GetMapping("/file/{id}")
    public void getFileById(@PathVariable Long id,HttpServletResponse response) throws IOException {
        stPythonAlgorithmFileInfoService.getPyFile(response,id);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        return stPythonAlgorithmFileInfoService.removeById(id) ? "删除成功" : "删除失败";
    }

}
