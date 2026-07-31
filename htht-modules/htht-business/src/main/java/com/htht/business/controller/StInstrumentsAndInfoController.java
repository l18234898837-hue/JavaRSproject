package com.htht.business.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StInstruments;
import com.htht.business.domain.StInstrumentsInfo;
import com.htht.business.domain.dto.StInstrumentsAndInfoDTO;
import com.htht.business.service.StInstrumentsInfoService;
import com.htht.business.service.StInstrumentsService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 航线+航线信息工具类
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/StInstrumentAndInfo")
public class StInstrumentsAndInfoController {

    private final StInstrumentsService stInstrumentsService;
    private final StInstrumentsInfoService stInstrumentsInfoService;

    @PostMapping()
    public R<Void> add(@Validated @RequestBody StInstrumentsAndInfoDTO stInstrumentsAndInfoDTO){
        StInstruments stInstruments = stInstrumentsAndInfoDTO.toStInstruments();
        StInstrumentsInfo stInstrumentsInfo = stInstrumentsAndInfoDTO.toStInstrumentsInfo();
        stInstrumentsInfoService.save(stInstrumentsInfo);
        stInstruments.setInstrumentInfoId(stInstrumentsInfo.getId());
        return stInstrumentsService.save(stInstruments)?R.ok():R.fail();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id){
        return stInstrumentsService.removeById(id)?R.ok():R.fail();
    }

    @PutMapping()
    public R<Void> update(@Validated @RequestBody StInstruments stInstruments){
        return stInstrumentsService.updateById(stInstruments)?R.ok():R.fail();
    }

    @GetMapping()
    public R<List<StInstrumentsAndInfoDTO>> list(){
        List<StInstruments> list = stInstrumentsService.list();
        return R.ok(getDtoList(list));
    }

    @GetMapping("/{id}")
    public R<StInstrumentsAndInfoDTO> getById(@PathVariable Long  id){
        StInstruments stInstrumentsServiceById = stInstrumentsService.getById(id);
        StInstrumentsInfo stInstrumentsInfoServiceById = stInstrumentsInfoService.getById(stInstrumentsServiceById.getInstrumentInfoId());
        StInstrumentsAndInfoDTO stInstrumentsAndInfoDTO = new StInstrumentsAndInfoDTO(stInstrumentsServiceById, stInstrumentsInfoServiceById);
        return R.ok(stInstrumentsAndInfoDTO);
    }

    @GetMapping("/code")
    public R<List<StInstrumentsAndInfoDTO>> getByCodeOrName(@RequestParam String code){
        LambdaQueryWrapper<StInstruments> wrapperTwo =new LambdaQueryWrapper<>();
        wrapperTwo.like(StInstruments::getInstrumentCode,code);
        List<StInstruments> list = stInstrumentsService.list(wrapperTwo);
        return R.ok(getDtoList(list));
    }

    @GetMapping("/timeBetween")
    public R<List<StInstrumentsAndInfoDTO>> getByTimeBetween(@RequestParam String startTime, @RequestParam String endTime){
        LocalDate start = LocalDate.parse(startTime);
        LocalDate end = LocalDate.parse(endTime);
        LambdaQueryWrapper<StInstruments> wrapper =new LambdaQueryWrapper<>();
        wrapper.between(StInstruments::getCommissionDate,start,end);
        List<StInstruments> list = stInstrumentsService.list(wrapper);
        return R.ok(getDtoList( list));
    }

    @GetMapping("/company")
    public R<List<StInstrumentsAndInfoDTO>> getByCompany(@RequestParam String company){
        LambdaQueryWrapper<StInstrumentsInfo> wrapper =new LambdaQueryWrapper<>();
        wrapper.like(StInstrumentsInfo::getDistributor,company).or().like(StInstrumentsInfo::getManufacturer,company);
        List<Long> collect = stInstrumentsInfoService.list(wrapper).stream().map(StInstrumentsInfo::getId).collect(Collectors.toList());
        LambdaQueryWrapper<StInstruments> wrapperTwo =new LambdaQueryWrapper<>();
        wrapperTwo.in(StInstruments::getInstrumentInfoId,collect);
        return R.ok(getDtoList(stInstrumentsService.list(wrapperTwo)));
    }

    @GetMapping("/voyages")
    public R<List<StInstrumentsAndInfoDTO>> getByVoyages(@RequestParam String voyage){
        LambdaQueryWrapper<StInstruments> wrapper =new LambdaQueryWrapper<>();
        wrapper.like(StInstruments::getUseVoyage,voyage);
        return R.ok(getDtoList(stInstrumentsService.list(wrapper)));
    }

    private List<StInstrumentsAndInfoDTO> getDtoList(List<StInstruments> list){
        return list.stream().map(stInstruments ->
            new StInstrumentsAndInfoDTO(stInstruments, stInstrumentsInfoService.getById(stInstruments.getInstrumentInfoId()))
        ).collect(Collectors.toList());
    }

}
