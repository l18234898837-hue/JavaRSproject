package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StDownloadRecord;
import com.htht.algorithm.data.mapper.StDownloadRecordMapper;
import com.htht.algorithm.data.service.StDownloadRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.helper.LoginHelper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * <p>
 * 数据下载记录表，记录用户提交的下载任务信息 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-11
 */
@Service
public class StDownloadRecordServiceImpl extends ServiceImpl<StDownloadRecordMapper, StDownloadRecord> implements StDownloadRecordService {

    @Override
    public boolean add(StDownloadRecord record) {
        Long userId = LoginHelper.getUserId();
        record.setSubmitUserId(userId);
        record.setDownloadTime(LocalDateTime.now());
        record.setSerialNumber(getNewSerialNumberByUserId(userId));
        return save(record);
    }

    @Override
    public int getNewSerialNumberByUserId(Long userId) {
        LambdaQueryWrapper<StDownloadRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StDownloadRecord::getSubmitUserId, userId);
        List<StDownloadRecord> list = list(queryWrapper);
        return list.stream()
                .map(StDownloadRecord::getSerialNumber)
                .max(Integer::compareTo)
                .orElse(1);
    }

    @Override
    public TableDataInfo<StDownloadRecord> listPage(StDownloadRecord stDownloadRecord, PageQuery pageQuery) {
        return TableDataInfo.build(
            this.lambdaQuery()
            .like(Strings.isNotBlank(stDownloadRecord.getDataName()), StDownloadRecord::getDataName, stDownloadRecord.getDataName())
            .like(Strings.isNotBlank(stDownloadRecord.getDataLevel()), StDownloadRecord::getDataLevel, stDownloadRecord.getDataLevel())
            .eq(ObjectUtils.isNotEmpty(stDownloadRecord.getSubmitUserId()), StDownloadRecord::getSubmitUserId, stDownloadRecord.getSubmitUserId())
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))

        );
    }


}
