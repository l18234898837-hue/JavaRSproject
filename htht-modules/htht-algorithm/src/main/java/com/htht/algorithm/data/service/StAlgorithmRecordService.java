package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StAlgorithmRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.domain.vo.SubmitPublicVo;
import com.htht.algorithm.data.domain.vo.SubmitUserVo;
import com.htht.common.core.domain.R;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 算法运行记录表，用于追踪每次算法执行的状态、用户、日志及运行目录 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-18
 */
public interface StAlgorithmRecordService extends IService<StAlgorithmRecord> {

    void canStart();
    void canStart(Long userId);

    StAlgorithmRecord getUserResult(Long userId);

    void addSetting(MultipartFile file) ;

    void add(AddAlgorithmTaskVo aATV);

    void add(Long userId, AddAlgorithmTaskVo addAlgorithmTaskVo);

    void mvFile(AddAlgorithmTaskVo aATV,StAlgorithmRecord record) throws IOException;

    void task();
    Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId();
    Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId(Long algorithmId);

    void submitUser( SubmitUserVo suv);

    void submitPublic(SubmitPublicVo spv);

    void checkCount(int count,Long userId);


}
