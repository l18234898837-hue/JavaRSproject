package com.htht.algorithm.data.core.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.core.constants.DataConstants;
import com.htht.algorithm.data.core.service.CommonService;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.BusFeatureFieldInfo;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusFeatureFieldInfoService;
import com.htht.algorithm.data.service.IBusFeatureInfoService;
import com.htht.algorithm.data.utils.GeoTools;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.tile.vector.entity.ShpInfo;
import com.htht.algorithm.data.tile.vector.service.VectorService;
import com.htht.algorithm.data.tile.vector.utils.ShpUtil;
import com.htht.algorithm.data.tile.utils.CmdCallBack;
import com.htht.algorithm.data.tile.utils.ZipUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class VectorDataService extends VectorService<Long> implements CommonService {

    private static final String[] COM_TYPES = new String[]{"zip", "tar", "7z"};

    private final String[] SHP_TYPES = new String[]{"shp", "shx", "dbf", "prj"};

    private IBusFileInfoService fileInfoService;
    private IBusDataInfoService dataInfoService;

    private IBusFeatureInfoService featureInfoService;
    private IBusFeatureFieldInfoService featureFieldInfoService;

    public VectorDataService(TileProperties tileProperties,
                             IBusFileInfoService fileInfoService,
                             IBusDataInfoService dataInfoService,
                             IBusFeatureInfoService featureInfoService,
                             IBusFeatureFieldInfoService featureFieldInfoService
    ) {
        super(tileProperties);
        this.fileInfoService = fileInfoService;
        this.dataInfoService = dataInfoService;
        this.featureInfoService = featureInfoService;
        this.featureFieldInfoService = featureFieldInfoService;
    }


    private void checkShpFileComplete(String shpPath) {
        File shpFile = new File(shpPath);
        List<String> nameList = null;
        String suffix = FileUtils.getSuffix(shpFile);
        if ("zip".equals(suffix)) {
            nameList = ZipUtil.listZip(shpFile);
        } else if ("7z".equals(suffix)) {
            nameList = ZipUtil.list7z(shpFile);
        } else if ("tar".equals(suffix)) {
            nameList = ZipUtil.listTar(shpFile);
        } else {
            throw new GlobalException("不支持的压缩格式:" + suffix);
        }
        if (nameList != null && nameList.size() >= SHP_TYPES.length) {
            for (String type : SHP_TYPES) {
                boolean flag = false;
                for (String name : nameList) {
                    if (FileUtils.getSuffix(name).equals(type)) {
                        flag = true;
                        break;
                    }
                }
                if (!flag) {
                    throw new GlobalException("请检查shp压缩包是否包含：" + type);
                }
            }
        } else {
            throw new GlobalException("请检查shp压缩包是否包含：" + Arrays.toString(SHP_TYPES));
        }
    }

    @Override
    public BusFileInfo upload(MultipartFile file) throws IOException {
        checkFileType(file);
        BusFileInfo fileInfo = fileInfoService.uploadFile(file, tileProperties.getVector().getUploadPath());
        checkShpFileComplete(fileInfo.getPath());
        return fileInfo;
    }

    @Override
    @Pooled(executor = "vectorStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void storage(BusDataInfo dataInfo) {
        //更改入库状态为入库中
        updateStock(dataInfo, DataConstants.DATA_STOCK_ING);

        try {
            BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
            //1-解压zip文文件
            String shpDir = tileProperties.getVector().getUnzipDir() + File.separator + dataInfo.getId();
            String shpPath = unShpZip(fileInfo.getPath(), shpDir);
            //2-读取shp文件信息
            ShpInfo info = getInfo(shpPath);
            dataInfo.setMetaData(JSONObject.toJSONString(info));
            //3-将shp信息存入数据库中
            dataInfo.setLngMin(info.getLngMin());
            dataInfo.setLngMax(info.getLngMax());
            dataInfo.setLatMin(info.getLatMin());
            dataInfo.setLatMax(info.getLatMax());
            String tileDir = tileProperties.getVector().getTileDir() + "/" + dataInfo.getId();
            dataInfo.setExtent(ShpUtil.createWkt(info.getLngMin(), info.getLngMax(), info.getLatMin(), info.getLatMax()));
            //TODO 因为切片需要时间很长，这里先录入坐标范围
            dataInfoService.updateById(dataInfo);
            //4-读取shp要素信息并入库
            GeoTools.shp2pgTable(shpPath, "shp_" + dataInfo.getId(), Charset.forName("GBK"));
            //这里将字段信息入库，方便后续配置字段别名
            Map<String, String> fieldMap = info.getFieldMap();
            //这里动态创建数据库表,并将数据录入表中,记录下表名

            int idx = 0;
            List<BusFeatureFieldInfo> featureFieldInfoList = new ArrayList<>();
            for (String field : fieldMap.keySet()) {
                idx++;
                BusFeatureFieldInfo featureFieldInfo = new BusFeatureFieldInfo();
                featureFieldInfo.setDataId(dataInfo.getId());
                featureFieldInfo.setField(field);
                featureFieldInfo.setFieldType(fieldMap.get(field));
                featureFieldInfo.setIsShow(false);
                featureFieldInfo.setIdx(idx);
                featureFieldInfoList.add(featureFieldInfo);
            }
            featureFieldInfoService.saveBatch(featureFieldInfoList);
            //5-将shp文件切片
            tile(shpPath, tileDir, tileProperties.getVector().getMinZoom(), tileProperties.getVector().getMaxZoom(), new CmdCallBack() {
                @Override
                public void success() {
                    //更改入库状态为已入库
                    updateStock(dataInfo, DataConstants.DATA_STOCK_SUCCESS);
                }

                @Override
                public void failed() {
                    //更改入库状态为入库失败
                    updateStock(dataInfo, DataConstants.DATA_STOCK_ERROR);
                }
            });
        } catch (Exception e) {
            updateStock(dataInfo, DataConstants.DATA_STOCK_ERROR);
        }
    }

    /**
     * 解压shp文件并返回对应shp地值
     */

    public String getShpZipFile(BusDataInfo dataInfo)
    {
        BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
        //1-解压zip文文件
        String shpDir = tileProperties.getVector().getUnzipDir() + File.separator + dataInfo.getId();
        return unShpZip(fileInfo.getPath(), shpDir);
    }

    /**
     * 更改入库状态
     *
     * @param dataInfo
     * @param stock
     */
    private void updateStock(BusDataInfo dataInfo, String stock) {
        dataInfo.setStock(stock);
        dataInfoService.updateDataInfo(dataInfo);
    }

    public void checkFileType(MultipartFile file) {
        if (!Arrays.asList(COM_TYPES).contains(FileUtils.getSuffix(file.getOriginalFilename()))) {
            throw new GlobalException("文件格式不正确，请上传：" + Arrays.toString(COM_TYPES));
        }
    }

    @Override
    public String getPath(Long id) {
        BusDataInfo dataInfo = dataInfoService.getDataInfoById(id);
        if (dataInfo.getFileId() != null) {
            BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
            return fileInfo.getPath();
        }
        throw new GlobalException("获取数据资源文件路径发生异常");
    }


}
