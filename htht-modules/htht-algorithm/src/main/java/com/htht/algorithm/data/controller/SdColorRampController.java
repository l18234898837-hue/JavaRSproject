package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.SdColorRamp;
import com.htht.algorithm.data.domain.vo.ColorRampVo;
import com.htht.algorithm.data.service.SdColorRampService;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.awt.*;

/**
 * 热力图色带配置表 前端控制器
 *
 * @author MSFY
 * @since 2026-03-05
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/sdColorRamp")
public class SdColorRampController {

    final SdColorRampService sdColorRampService;

    @PostMapping
    public R<String> add(@RequestBody ColorRampVo colorRampVo) {
        colorRampVo.checkColorRamp();
        SdColorRamp sdColorRamp = colorRampVo.toSdColorRamp();
        return R.ok(sdColorRampService.save(sdColorRamp) ? "添加成功" : "添加失败");
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        if(id.equals(1L)){{
            throw new GlobalException("无法删除默认色带");
        }}
        return R.ok(sdColorRampService.removeById(id) ? "删除成功" : "删除失败");
    }

    @PutMapping
    public R<String> update(@RequestBody ColorRampVo colorRampVo) {
        if(colorRampVo.getId().equals(1L)){
            SdColorRamp byId = sdColorRampService.getById(colorRampVo.getId());
            if(!byId.getName().equals(colorRampVo.getName())){
                throw new GlobalException("无法修改默认色带名称");
            }
        }

        colorRampVo.checkColorRamp();
        SdColorRamp sdColorRamp = colorRampVo.toSdColorRamp();
        sdColorRamp.setId(colorRampVo.getId());
        return R.ok(sdColorRampService.updateById(sdColorRamp) ? "修改成功" : "修改失败");
    }

    @GetMapping
    public TableDataInfo<SdColorRamp> query(@RequestParam String name) {
        return TableDataInfo.build(sdColorRampService.lambdaQuery()
            .like(StringUtils.isNotBlank(name),SdColorRamp::getName, name).list());
    }

}
