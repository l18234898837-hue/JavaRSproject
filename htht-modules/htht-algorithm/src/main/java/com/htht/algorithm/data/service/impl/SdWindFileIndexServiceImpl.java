package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.SdWindFileIndex;
import com.htht.algorithm.data.domain.vo.WDSDQuery;
import com.htht.algorithm.data.mapper.SdWindFileIndexMapper;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.SdWindFileIndexService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import me.msfy.domain.DataVariable;
import me.msfy.utils.JsonUtils;
import me.msfy.windsd.WindSD;
import me.msfy.windsd.domain.WindData;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * <p>
 * 风向记录 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2026-05-20
 */
@Service
@RequiredArgsConstructor
public class SdWindFileIndexServiceImpl extends ServiceImpl<SdWindFileIndexMapper, SdWindFileIndex> implements SdWindFileIndexService {

    private final IBusDataInfoService busDataInfoService;
    private final IBusFileInfoService busFileInfoService;
    public final TileProperties tileProperties;



    @Override
    public void checkData(WDSDQuery query) {
        BusDataInfo directionData = busDataInfoService.getDataInfoById(query.getDirectionDataId());
        BusDataInfo speedData = busDataInfoService.getDataInfoById(query.getSpeedDataId());
        String[] dirSplit = directionData.getName().split("-");
        String[] spdSplit = speedData.getName().split("-");
        if(!dirSplit[0].equals(spdSplit[0])){
            throw new GlobalException("检查传入文件是否是配套文件");
        }
        if(!dirSplit[1].contains("dir")||!spdSplit[1].contains("spd")){
            throw new GlobalException("检查传入文件是否正常");
        }

    }

    @Override
    public List<WindData> genData(WDSDQuery query) throws Exception {
        SdWindFileIndex windFileIndex = lambdaQuery().eq(SdWindFileIndex::getDirectionFileId, query.getDirectionDataId())
            .eq(SdWindFileIndex::getSpeedFileId, query.getSpeedDataId()).one();
        if (windFileIndex == null){
            BusDataInfo directionData = busDataInfoService.getDataInfoById(query.getDirectionDataId());
            BusDataInfo speedData = busDataInfoService.getDataInfoById(query.getSpeedDataId());
            BusFileInfo dirFileInfo = busFileInfoService.getFileInfoById(directionData.getFileId());
            BusFileInfo spdFileInfo = busFileInfoService.getFileInfoById(speedData.getFileId());
            String WDSDJSon = Path.of(tileProperties.getGrid().getThumbPath() , directionData.getId() + "-WDSD.json").toString();
            if(directionData.getName().split("-")[1].contains("near")){
                WindSD.toWindSdJson(
                    dirFileInfo.getPath(),
                    new DataVariable().setContentPath("nearshoredir").setLonPath("lon").setLatPath("lat"),
                    spdFileInfo.getPath(),
                    new DataVariable().setContentPath("nearshorespd").setLonPath("lon").setLatPath("lat"),
                    WDSDJSon,
                    false
                );
            }else if(directionData.getName().split("-")[1].contains("off")){
                WindSD.toWindSdJson(
                    dirFileInfo.getPath(),
                    new DataVariable().setContentPath("offshoredir").setLonPath("lon").setLatPath("lat"),
                    spdFileInfo.getPath(),
                    new DataVariable().setContentPath("offshorespd").setLonPath("lon").setLatPath("lat"),
                    WDSDJSon,
                    true
                );
            }else{
                throw new GlobalException("检查传入文件是否正常");
            }
            SdWindFileIndex sdWindFileIndex = new SdWindFileIndex().setSpeedFileId(query.getSpeedDataId()).setDirectionFileId(query.getDirectionDataId()).setJsonFilePath(WDSDJSon);
            this.save(sdWindFileIndex);
            return JsonUtils.readWindDataFromJson(WDSDJSon);
        }else{
            return JsonUtils.readWindDataFromJson(windFileIndex.getJsonFilePath());
        }



    }
}
