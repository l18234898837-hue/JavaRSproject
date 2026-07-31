package com.htht.business.service;

import com.htht.business.domain.StRadarDataInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>
 * 雷达数据信息关联表，用于关联雷达数据与业务场景下的扩展信息 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-23
 */
public interface StRadarDataInfoService extends IService<StRadarDataInfo> {

    void add(MultipartFile file);
    TableDataInfo<StRadarDataInfo>  page(PageQuery pageQuery);
    void download(Long id, HttpServletResponse response) throws IOException;
}
