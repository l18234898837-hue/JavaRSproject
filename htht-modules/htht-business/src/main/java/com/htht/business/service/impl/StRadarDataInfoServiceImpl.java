package com.htht.business.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.domain.StRadarDataInfo;
import com.htht.business.mapper.StRadarDataInfoMapper;
import com.htht.business.service.StRadarDataInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>
 * 雷达数据信息关联表，用于关联雷达数据与业务场景下的扩展信息 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-23
 */
@Service
@RequiredArgsConstructor
public class StRadarDataInfoServiceImpl extends ServiceImpl<StRadarDataInfoMapper, StRadarDataInfo> implements StRadarDataInfoService {
    private final ISysFileService iSysFileService;
    @Override
    public void add(MultipartFile file) {
        SysFile upload = iSysFileService.upload(file);
        StRadarDataInfo stRadarDataInfo = new StRadarDataInfo()
            .setDataName(upload.getFileName())
            .setFileId(upload.getId());
        save(stRadarDataInfo);
    }

    @Override
    public TableDataInfo<StRadarDataInfo> page(PageQuery pageQuery) {
        return TableDataInfo.build(this.lambdaQuery().page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize())));
    }

    @Override
    public void download(Long id, HttpServletResponse response) throws IOException {
        StRadarDataInfo byId = getById(id);
        iSysFileService.download(response, byId.getFileId(),"");
    }
}
