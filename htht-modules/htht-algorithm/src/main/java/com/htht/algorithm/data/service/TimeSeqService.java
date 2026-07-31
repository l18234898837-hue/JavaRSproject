package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.dto.TimeSeqPointDto;
import com.htht.algorithm.data.domain.dto.TimeSeqRectDto;
import com.htht.algorithm.data.domain.vo.TimeSeqVo;

import java.io.IOException;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/8 15:01
 **/
public interface TimeSeqService {
    List<TimeSeqVo> timeSeqPoint(TimeSeqPointDto timeSeqPointDto) throws Exception;

    List<TimeSeqVo> timeSeqRect(TimeSeqRectDto timeSeqRectDto) throws Exception;
}
