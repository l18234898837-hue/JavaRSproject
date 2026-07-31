package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.business.domain.SdObserveData;
import com.htht.business.domain.SdWavelengthData;
import com.htht.business.domain.dto.QuerySdObserveDataDto;
import com.htht.business.domain.vo.SdObserveDataVo;
import com.htht.business.domain.vo.SdObserveIntervalVo;
import com.htht.business.service.SdObserveDataService;
import com.htht.business.service.SdObserveJoinService;
import com.htht.business.service.SdObserveListService;
import com.htht.business.service.SdWavelengthDataService;
import com.htht.business.utils.ExcelUtil;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 *
 * 卫星项目观测数据，包含以下表格内容
 * 卫星数据表
 * 卫星数据连接表
 * 卫星集合表
 * 水深数据表
 *
 * @author MSFY
 * @since 2025-10-30
 */
@RestController
@RequestMapping("/sd/observe")
@RequiredArgsConstructor
public class SdObserveListController {
    private final SdObserveDataService sdObserveDataService;
    private final SdWavelengthDataService sdWavelengthDataService;

    @SaCheckPermission("sd:observe:add")
    @PostMapping("/addFile")
    public R<Void> addObserveDataAndWavelength(@RequestPart("file") MultipartFile file){
        try(Workbook workbook = WorkbookFactory.create(file.getInputStream())){
            for(int sheetIndex = 0;sheetIndex<workbook.getNumberOfSheets();sheetIndex++){
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                if(sheetName.equals("spectrum")){
                    List<SdWavelengthData> waveLengthData = ExcelUtil.getWaveLengthData(sheet);
                    sdWavelengthDataService.saveBatch(waveLengthData);
                }else if(sheetName.equals("metadata")){
                    List<SdObserveData> observeData = ExcelUtil.getObserveData(sheet);
                    sdObserveDataService.saveBatch(observeData);
                }
            }
        }catch  ( IOException e){
            throw new GlobalException(e.getMessage());
        }
        return R.ok();
    }

    @SaCheckPermission("sd:observe:query")
    @PostMapping("/query")
    public R<TableDataInfo<SdObserveDataVo>>  query(@RequestBody @Validated QuerySdObserveDataDto  query, @RequestBody PageQuery pageQuery){
        TableDataInfo<SdObserveDataVo> sdObserveDataVos = sdObserveDataService.query(query,pageQuery);
        sdObserveDataVos.getRows().forEach(o->{
            o.setWaveLengthDataList( sdWavelengthDataService.lambdaQuery()
                .eq(SdWavelengthData::getSdIdOrSite, o.getSdId())
                .between(SdWavelengthData::getWavelength, query.getWavelengthStart(), query.getWavelengthEnd())
                .list());
        });
        sdObserveDataVos.getRows().removeIf(o ->
            o.getWaveLengthDataList() == null || o.getWaveLengthDataList().isEmpty()
        );
        return R.ok(sdObserveDataVos);
    }

    @SaCheckPermission("sd:observe:query")
    @GetMapping("/site")
    public R<List<String>> querySite(){
        List<String> site = sdObserveDataService.querySite();
        return R.ok(site);
    }

    @SaCheckPermission("sd:observe:query")
    @GetMapping("/device")
    public R<List<String>> queryDevice(){
        List<String> device = sdObserveDataService.queryDevice();
        return R.ok(device);
    }

    @SaCheckPermission("sd:observe:query")
    @GetMapping("/interval")
    public R<SdObserveIntervalVo> getInterval(){
       return R.ok(sdObserveDataService.getInterval());
    }


}
