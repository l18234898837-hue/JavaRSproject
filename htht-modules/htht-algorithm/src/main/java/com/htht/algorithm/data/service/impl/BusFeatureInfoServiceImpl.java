package com.htht.algorithm.data.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.exception.GlobalException;
import com.htht.algorithm.data.domain.BusFeatureInfo;
import com.htht.algorithm.data.mapper.BusFeatureInfoMapper;
import com.htht.algorithm.data.service.IBusFeatureInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
@Service
@RequiredArgsConstructor
public class BusFeatureInfoServiceImpl extends ServiceImpl<BusFeatureInfoMapper, BusFeatureInfo> implements IBusFeatureInfoService {

    @Override
    public void addFeatureInfo(BusFeatureInfo featureInfo) {
        this.save(featureInfo);
    }

    @Override
    public void addFeatureInfoBatch(Long dataId, List<Map<String, Object>> resultList, int batchSize) {
        List<BusFeatureInfo> featureInfoList = resultList.stream().map(map -> {
            BusFeatureInfo featureInfo = new BusFeatureInfo();
            Object fIdObj = map.get("FID");
            if (fIdObj == null) {
                throw new GlobalException("要素属性不存在FID");
            }
            Long fId = (Long) fIdObj;
            featureInfo.setDataId(dataId);
            featureInfo.setFId(fId);
            featureInfo.setJson(JSONUtil.toJsonStr(map));
            return featureInfo;
        }).collect(Collectors.toList());
        this.saveBatch(featureInfoList, batchSize);
    }

    @Override
    public JSONObject getFeatureByDataIdAndFid(Long dataId, Long fId) {
        BusFeatureInfo info = this.lambdaQuery()
            .eq(BusFeatureInfo::getDataId, dataId)
            .eq(BusFeatureInfo::getFId, fId)
            .one();
        if (info != null) {
            return JSONUtil.parseObj(info.getJson());
        }
        throw new GlobalException("要素不存在");
    }
}
