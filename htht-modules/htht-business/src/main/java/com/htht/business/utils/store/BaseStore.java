package com.htht.business.utils.store;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusCatalogInfo;
import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.algorithm.data.tile.utils.ZipUtil;
import com.htht.business.domain.StStoreData;
import com.htht.business.service.StStoreDataService;
import com.htht.common.exception.GlobalException;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO 该方法以及底下的所有子类全部废弃，不再调用，只做参考使用，子类有一下特殊类型的入库样例
 * DateTime: 2025/7/24 16:40
 **/
@Service
@RequiredArgsConstructor
public abstract class BaseStore {
    private final TileProperties tileProperties;
    protected final ISysFileService sysFileService;
    private final IBusMenuInfoService menuInfoService;
    private final IBusCatalogInfoService catalogInfoService;
    protected final IBusDataInfoService dataInfoService;



    public BusMenuInfo run(StStoreData stStoreData){
        if(!menuInfoService.checkMenuNameUnique(new BusMenuInfo().setMenuName(stStoreData.getName()))){
            throw new GlobalException("已有同名文件解压处理过");
        }
        if(!menuInfoService.checkMenuNameUnique(new BusMenuInfo().setMenuName(stStoreData.getName()))){
            throw new GlobalException("已有同名文件入库");
        }

        File directory = unZip(stStoreData);
        List<Long> longList = prepareHandle(stStoreData, directory);
        return LinkCatalog(stStoreData, longList);
    };

    protected File unZip(StStoreData stStoreData) {
        SysFile fileInfo = sysFileService.getById(stStoreData.getFileId() );
        File file= new File(fileInfo.getFilePath());
        Path path= Paths.get(tileProperties.getVector().getStoreDir(),stStoreData.getId().toString());
//        String unZipDir = tileProperties.getVector().getStoreDir() + "/" + fileInfo.getId();
        String unZipDir = path.toString();
        switch(fileInfo.getExtension()){
            case "zip":
                ZipUtil.unZip(file, unZipDir,false);
                break;
            case "tar":
                ZipUtil.unTar(file, unZipDir,false);
                break;
            case "gz":
                ZipUtil.unTarGz(file, unZipDir,false);
                break;
            default:
                throw new GlobalException("暂不支持该文件类型");
        }
        File directory = new File(unZipDir);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new GlobalException("目录不存在或解压失败");
        }
        return directory;
    };

    /**
     * 处理解压之后的文件
     * 保证转化成geoTiff，然后存储在dataInfo中，然后把对应的id返回出来即可
     */

    protected abstract List<Long> prepareHandle(StStoreData stStoreData,File unZipFile);


    protected BusMenuInfo LinkCatalog(StStoreData stStoreData,List<Long> dataIdList) {
        if(dataIdList.isEmpty()){
            throw new GlobalException("文件扫描之后没有找到入库内容");
        }
        for (Long id : dataIdList) {
            dataInfoService.storage(id);
        }
        String menuStr = StoreDataType.getMenuStr(1);
        BusMenuInfo busMenuInfo=new BusMenuInfo()
            .setMenuName(stStoreData.getName())
            .setOrderNum(1)
            .setMenuType("C")
            .setBindType("2");
        if(!menuStr.isEmpty()){
            BusCatalogInfo busCatalogInfo = new BusCatalogInfo().setName(stStoreData.getName());
            catalogInfoService.addCatalog(busCatalogInfo);
            for (Long l : dataIdList) {
                catalogInfoService.bindDataToCatalog(l,busCatalogInfo.getId());
            }
            Long pid = menuInfoService.getIdByMenuName(menuStr);
            busMenuInfo
                .setParentId(pid)
                .setCatalogId(busCatalogInfo.getId());
            menuInfoService.addMenu(busMenuInfo);
            stStoreData.setStatus((short)2);
        }else{
            throw  new GlobalException("暂不支持该数据类型");
        }
        return busMenuInfo;
    };

}
