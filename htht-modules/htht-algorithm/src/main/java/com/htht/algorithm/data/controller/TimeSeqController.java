package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.dto.TimeSeqPointDto;
import com.htht.algorithm.data.domain.dto.TimeSeqRectDto;
import com.htht.algorithm.data.domain.vo.TimeSeqVo;
import com.htht.algorithm.data.service.TimeSeqService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 * 时序工具类，获取点时序或者矩形时序
 *
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/8 14:58
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/timeSeq")
public class TimeSeqController {
    private final TimeSeqService timeSeqService;

    @PostMapping("/point")
    public R<List<TimeSeqVo>> timeSeqPoint(@RequestBody TimeSeqPointDto timeSeqPointDto) {
        try {
            return R.ok(timeSeqService.timeSeqPoint(timeSeqPointDto));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/rect")
    public R<List<TimeSeqVo>> timeSeqRect(@RequestBody TimeSeqRectDto timeSeqRectDto) {
        try {
            return R.ok(timeSeqService.timeSeqRect(timeSeqRectDto));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

}
