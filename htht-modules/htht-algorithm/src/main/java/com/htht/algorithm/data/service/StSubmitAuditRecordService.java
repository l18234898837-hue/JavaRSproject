package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StAlgorithmRecordCatalogInfo;
import com.htht.algorithm.data.domain.StSubmitAuditRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.StSubmitAuditRecordVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 提交数据审核表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-30
 */
public interface StSubmitAuditRecordService extends IService<StSubmitAuditRecord> {

    void add(StSubmitAuditRecord record, List<StAlgorithmRecordCatalogInfo> list);

    boolean delectBy(Long id);

    TableDataInfo<StSubmitAuditRecordVo>  page(Long status, String nameKey, PageQuery pageQuery);


    void audit(Long recordId, Integer status);

    void auditData(Long recordId, Long dataId, Integer status);
}
