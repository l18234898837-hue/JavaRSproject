package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.domain.StAlgorithmParamInfo;
import com.htht.algorithm.data.mapper.StAlgorithmParamInfoMapper;
import com.htht.algorithm.data.service.StAlgorithmParamInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.common.helper.LoginHelper;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 算法参数信息表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-23
 */
@Service
@RequiredArgsConstructor
public class StAlgorithmParamInfoServiceImpl extends ServiceImpl<StAlgorithmParamInfoMapper, StAlgorithmParamInfo> implements StAlgorithmParamInfoService {

    private final ISysFileService sysFileService;

    @Override
    public StAlgorithmParamInfo add(MultipartFile file, int type) {
        StAlgorithmParamInfo stAlgorithmParamInfo = new StAlgorithmParamInfo();
        SysFile upload = sysFileService.upload(file);
        if(type==1){
            upload.setFileName(LoginHelper.getUsername()+"_"+ DateUtil.getNowFormatterStr("yyyyMMddHHmmss")+"_setting");
            sysFileService.updateById(upload);
        }
        stAlgorithmParamInfo.setUserid(LoginHelper.getUserId()).setFileId(upload.getId()).setType(type);
        save(stAlgorithmParamInfo);
        return stAlgorithmParamInfo;
    }
}
