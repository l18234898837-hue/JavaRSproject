package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StAlgorithmParamInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 算法参数信息表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-23
 */
public interface StAlgorithmParamInfoService extends IService<StAlgorithmParamInfo> {


    StAlgorithmParamInfo add(MultipartFile file, int type);
}
