package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StVoyageJoinInstruments;
import com.htht.business.service.StVoyageJoinInstrumentsService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 *  航线链接设备控制器
 *
 * @author MSFY
 * @since 2025-07-09
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/stVoyageJoinInstruments")
public class StVoyageJoinInstrumentsController {
    private final StVoyageJoinInstrumentsService stVoyageJoinInstrumentsService;
    private final ISysFileService iSysFileService;

    @PostMapping
    public R<Void> add(@Validated @RequestBody StVoyageJoinInstruments stVoyageJoinInstruments) {
        return stVoyageJoinInstrumentsService.save(stVoyageJoinInstruments)? R.ok():R.fail();
    }

    @DeleteMapping
    public R<Void> delete(@RequestBody StVoyageJoinInstruments stVoyageJoinInstruments) {
        return stVoyageJoinInstrumentsService.removeById(stVoyageJoinInstruments)? R.ok():R.fail();
    }

    @GetMapping
    public R<List<StVoyageJoinInstruments>> list(StVoyageJoinInstruments stVoyageJoinInstruments) {
        return R.ok(stVoyageJoinInstrumentsService.list(new LambdaQueryWrapper<StVoyageJoinInstruments>()
            .eq(Objects.nonNull(stVoyageJoinInstruments.getVoyageId()), StVoyageJoinInstruments::getVoyageId, stVoyageJoinInstruments.getVoyageId())
            .eq(Objects.nonNull(stVoyageJoinInstruments.getInstrumentId()), StVoyageJoinInstruments::getInstrumentId, stVoyageJoinInstruments.getInstrumentId())
            .eq(Objects.nonNull(stVoyageJoinInstruments.getDatasetId()), StVoyageJoinInstruments::getDatasetId, stVoyageJoinInstruments.getDatasetId())
        ));
    }

    @GetMapping("/page")
    public R<TableDataInfo<StVoyageJoinInstruments>> list(StVoyageJoinInstruments stVoyageJoinInstruments, PageQuery pageQuery) {
        return R.ok(stVoyageJoinInstrumentsService.selectPage(stVoyageJoinInstruments,pageQuery));
    }

    @GetMapping("/{datasetId}")
    public R<JSONObject> getById(@PathVariable Long  datasetId) {
       try {
           SysFile file = iSysFileService.getById(datasetId);
           if(file == null || file.getFilePath() == null){
               return R.fail("文件不存在");
           }
           Path path = Paths.get(file.getFilePath());
           String jsonContent = Files.readString( path, StandardCharsets.UTF_8);
           JSONObject jsonObject = JSONUtil.parseObj(jsonContent);
           return R.ok(jsonObject);
       } catch (Exception e) {
           return R.fail("读取文件失败");
       }
    }

    @SaCheckPermission("data:download:observe")
    @GetMapping("/nginx/{datasetId}")
    public void getNginxUrlById(@PathVariable Long  datasetId, HttpServletResponse response) throws IOException {
            SysFile file = iSysFileService.getById(datasetId);
            iSysFileService.download(response,file.getId(),"123123");

    }




}
