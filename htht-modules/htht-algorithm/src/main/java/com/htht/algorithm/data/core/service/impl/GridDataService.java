package com.htht.algorithm.data.core.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.htht.algorithm.data.computed.ComputedUtil;
import com.htht.algorithm.data.domain.SdColorRamp;
import com.htht.algorithm.data.domain.vo.ColorRampVo;
import com.htht.algorithm.data.enums.L2NcFileType;
import com.htht.algorithm.data.enums.NcFileType;
import com.htht.algorithm.data.service.*;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.domain.BusLegendInfo;
import com.htht.algorithm.data.core.constants.DataConstants;
import com.htht.algorithm.data.core.service.CommonService;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.tile.grid.entity.GridInfo;
import com.htht.algorithm.data.tile.grid.entity.LegendInfo;
import com.htht.algorithm.data.tile.grid.enums.ImageType;
import com.htht.algorithm.data.tile.grid.service.GridService;
import com.htht.algorithm.data.tile.grid.utils.GridUtil;
import com.htht.algorithm.data.tile.vector.utils.ShpUtil;
import jodd.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import me.msfy.domain.DataBound;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

@Service
@Slf4j
public class GridDataService extends GridService<Long> implements CommonService {

    private static final String[] TIFF_TYPES = new String[]{"tif", "tiff", "img", "ecw", "jp2","TIF","TIFF"};

    private IBusFileInfoService fileInfoService;
    private IBusDataInfoService dataInfoService;
    private IBusLegendInfoService legendInfoService;
    private IBusSentinelInfoService sentinelInfoService;
    private IBusAlgorithmInfoService algorithmInfoService;

    private SdColorRampService sdColorRampService;

    private LandsatScanStore landsatScanStore;
    private Sentinel2ScanStore sentinel2ScanStore;


    public GridDataService(TileProperties tileProperties,
                           IBusFileInfoService fileInfoService,
                           IBusDataInfoService dataInfoService,
                           IBusLegendInfoService legendInfoService,
                           IBusSentinelInfoService sentinelInfoService,
                           IBusAlgorithmInfoService algorithmInfoService,
                           LandsatScanStore landsatScanStore,
                           Sentinel2ScanStore sentinel2ScanStore,
                           SdColorRampService sdColorRampService
    ) {
        super(tileProperties);
        this.fileInfoService = fileInfoService;
        this.dataInfoService = dataInfoService;
        this.legendInfoService = legendInfoService;
        this.sentinelInfoService = sentinelInfoService;
        this.algorithmInfoService = algorithmInfoService;

        this.sdColorRampService = sdColorRampService;

        this.landsatScanStore = landsatScanStore;
        this.sentinel2ScanStore = sentinel2ScanStore;
    }

    @Override
    public String getPath(Long id) {
        BusDataInfo dataInfo = dataInfoService.getDataInfoById(id);
        if (dataInfo.getFileId() != null) {
            return dataInfo.getTransformPath();
        }
        throw new GlobalException("获取数据资源文件路径发生异常");
    }

    @Override
    public LegendInfo getLegendInfo(Long id, int type) {
        BusLegendInfo legendInfo = legendInfoService.getEnabledLegend(type, id);
        if (Objects.nonNull(legendInfo)) {
            //转换一下格式
            LegendInfo newLegend = JSONObject.parseObject(legendInfo.getLegend(), LegendInfo.class);
            newLegend.setId(legendInfo.getId());
            return newLegend;
        } else {
            return null;
        }

    }

    @Override
    public LegendInfo getLegendInfo2(Long id, String dataType, int type) {
        BusLegendInfo legendInfo = legendInfoService.getEnabledLegend2(type, id, dataType);
        if (Objects.nonNull(legendInfo)) {
            //转换一下格式
            LegendInfo newLegend = JSONObject.parseObject(legendInfo.getLegend(), LegendInfo.class);
            newLegend.setId(legendInfo.getId());
            return newLegend;
        } else {
            return null;
        }
    }

    @Override
    public BusFileInfo upload(MultipartFile file) throws IOException {
        checkFileType(file);
        BusFileInfo fileInfo = fileInfoService.uploadFile(file, tileProperties.getGrid().getUploadPath());
        return fileInfo;
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void storage(BusDataInfo dataInfo) {
        //更改状态为入库中
        updateStock(dataInfo, DataConstants.DATA_STOCK_ING);
        try {
            BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
            storage(dataInfo, fileInfo.getPath(), fileInfo);
        } catch (Exception e) {
            //更改入库状态为入库失败
            updateStock(dataInfo, DataConstants.DATA_STOCK_ERROR);
        }
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    //NC文件入库代码
    public void storageNC(BusDataInfo dataInfo) {
        //更改状态为入库中
        updateStock(dataInfo, DataConstants.DATA_STOCK_ING);
        try {
            String[] split = dataInfo.getName().split("_");
            NcFileType ncFileType = NcFileType.isNcFileType(split[0]);
            if(ncFileType != null){
                BusFileInfo fileInfo = fileInfoService.getById(dataInfo.getFileId());
                String ncFileToTifPath = NcFileType.NCFileTypeAutoHandle(ncFileType, fileInfo.getPath(),dataInfo.getName());
                storage(dataInfo, ncFileToTifPath, fileInfo);
            }
            L2NcFileType l2NcFileType = L2NcFileType.isNcFileType(dataInfo.getName());
            if(l2NcFileType!=null){
                BusFileInfo fileInfo = fileInfoService.getById(dataInfo.getFileId());
                storage(dataInfo,fileInfo,l2NcFileType);
            }


            if(ncFileType==null&&l2NcFileType==null){
                throw new GlobalException("未做兼容的NC/HDF文件，联系管理员");
            }


        } catch (Exception e) {
            //更改入库状态为入库失败
            updateStock(dataInfo, DataConstants.DATA_STOCK_ERROR);
            log.error("NC文件入库发生异常:{}", e.getMessage(), e);
        }
    }

    private void storage(BusDataInfo dataInfo, String tifPath, BusFileInfo fileInfo) throws Exception{
        GridInfo info = GridUtil.getInfo(tifPath);
        String thumbPath = Path.of(tileProperties.getGrid().getThumbPath() , dataInfo.getId() + ".jpg").toString();

        GridUtil.createThumb(tifPath, thumbPath,sdColorRampService.getDefaultColorRamp());
        dataInfo.setDisplayThumbnail(thumbPath);
        dataInfo.setDownloadThumbnail(thumbPath);
        dataInfo.setMetaData(JSONObject.toJSONString(info));
        File file = new File(tifPath);
        if (file.exists()) {
            //如果影像坐标系不为4326，则要进行影像投影转换
            if (!GridUtil.is4326proj(tifPath)) {
                String tranPath = file.getParent() + File.separator + fileInfo.getId() + "-tran." + FileUtils.getSuffix(file);
                GridUtil.projTransform(tifPath, tranPath, 4326);
                dataInfo.setOriginalPath(tifPath);
                dataInfo.setTransformPath(tranPath);
            } else {
                dataInfo.setOriginalPath(tifPath);
                dataInfo.setTransformPath(tifPath);
            }
            //TODO 栅格数据入库
            //建立栅格数据金字塔
            if (GridUtil.buildOverviews(dataInfo.getOriginalPath(), "BILINEAR".toLowerCase())) {
                GridInfo newInfo = GridUtil.getInfo(dataInfo.getTransformPath());
                dataInfo.setLngMin(newInfo.getLngMin());
                dataInfo.setLngMax(newInfo.getLngMax());
                dataInfo.setLatMin(newInfo.getLatMin());
                dataInfo.setLatMax(newInfo.getLatMax());

                dataInfo.setExtent(ShpUtil.createWkt(newInfo.getLngMin(), newInfo.getLngMax(), newInfo.getLatMin(), newInfo.getLatMax()));
                dataInfo.setStock(DataConstants.DATA_STOCK_SUCCESS);
                dataInfoService.updateDataInfo(dataInfo);
            }
        }

    }

    private void storage(BusDataInfo dataInfo, BusFileInfo fileInfo,L2NcFileType l2NcFileType) throws Exception{
        String thumbPath = Path.of(tileProperties.getGrid().getThumbPath() , dataInfo.getId() + ".jpg").toString();
        String jsonPath = Path.of(tileProperties.getGrid().getThumbPath() , dataInfo.getId() + ".json").toString();
        ColorRampVo defaultColorRamp = sdColorRampService.getDefaultColorRamp();
        L2NcFileType.createJson(fileInfo.getPath(), jsonPath, l2NcFileType);
        DataBound dataBound = L2NcFileType.createThumbByJson(jsonPath, thumbPath, l2NcFileType, defaultColorRamp);
        dataInfo.setOriginalPath(jsonPath);
        dataInfo.setTransformPath(jsonPath);
        dataInfo.setDisplayThumbnail(thumbPath);
        dataInfo.setDownloadThumbnail(thumbPath);
        dataInfo.setMetaData(JSONObject.toJSONString(dataBound));
        dataInfo.setLngMin(dataBound.getLonMin());
        dataInfo.setLngMax(dataBound.getLonMax());
        dataInfo.setLatMin(dataBound.getLatMin());
        dataInfo.setLatMax(dataBound.getLatMax());
        dataInfo.setExtent(ShpUtil.createWkt(dataBound.getLonMin(), dataBound.getLonMax(), dataBound.getLatMin(), dataBound.getLatMax()));
        dataInfo.setStock(DataConstants.DATA_STOCK_SUCCESS);
        dataInfoService.updateDataInfo(dataInfo);
    }

    /**
     * 压缩文件入库
     * @param dataInfo
     */
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void storageZip(BusDataInfo dataInfo) {
        //更改状态为入库中
        updateStock(dataInfo, DataConstants.DATA_STOCK_ING);
        try {
            BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
            File zipFile = new File(fileInfo.getPath());
            String unZipStr=Path.of(tileProperties.getGrid().getUploadPath(), fileInfo.getId().toString()).toString();
            Map<String, String> map;
            if(landsatScanStore.canL0Scan(fileInfo.getFilename())){
                 map = landsatScanStore.getStoreTif(zipFile, unZipStr);
            }else if(sentinel2ScanStore.canL0Scan(fileInfo.getFilename())){
                 map = sentinel2ScanStore.getStoreTif(zipFile, unZipStr);
            }else{
                throw new GlobalException("没有相关入库代码实现");
            }
            String tifPath = map.get("tifPath");
            String thumbPath = map.get("thumbPath");
            String saveThumbPath = Path.of(tileProperties.getGrid().getThumbPath(), dataInfo.getId() + ".jpg").toString();
            FileUtil.move(thumbPath, saveThumbPath);
            dataInfo.setDisplayThumbnail(saveThumbPath)
                .setDownloadThumbnail(saveThumbPath);
            GridInfo info = GridUtil.getInfo(tifPath);
            dataInfo.setMetaData(JSONObject.toJSONString(info));
            File file = new File(tifPath);
            if (file.exists()) {
                //如果影像坐标系不为4326，则要进行影像投影转换
                String tranPath = file.getParent() + File.separator + fileInfo.getId() + "-tran." + FileUtils.getSuffix(file);
                if (!GridUtil.is4326proj(tifPath)) {
                    GridUtil.projTransform(file.getPath(), tranPath, 4326);
                    dataInfo.setOriginalPath(fileInfo.getPath());
                    dataInfo.setTransformPath(tranPath);
                } else {
                    FileUtil.move(tifPath,tranPath);
                    dataInfo.setOriginalPath(fileInfo.getPath());
                    dataInfo.setTransformPath(tranPath);
                }
                //TODO 栅格数据入库
                //建立栅格数据金字塔
                GridInfo newInfo = GridUtil.getInfo(dataInfo.getTransformPath());
                dataInfo.setLngMin(newInfo.getLngMin())
                .setLngMax(newInfo.getLngMax())
                .setLatMin(newInfo.getLatMin())
                .setLatMax(newInfo.getLatMax());

                dataInfo.setExtent(ShpUtil.createWkt(newInfo.getLngMin(), newInfo.getLngMax(), newInfo.getLatMin(), newInfo.getLatMax()));
                dataInfo.setStock(DataConstants.DATA_STOCK_SUCCESS);
                dataInfoService.updateDataInfo(dataInfo);
            }

            FileStrUtil.deleteFolderRecursively(new File(unZipStr));
        } catch (Exception e) {
            //更改入库状态为入库失败
            updateStock(dataInfo, DataConstants.DATA_STOCK_ERROR);
        }
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

    /**
     * 检查上传的文件类型是否正确
     *
     * @param file
     */
    public void checkFileType(MultipartFile file) {
        if (!Arrays.asList(TIFF_TYPES).contains(FileUtils.getSuffix(file.getOriginalFilename()))) {
            throw new GlobalException("文件格式不正确，请上传：" + Arrays.toString(TIFF_TYPES));
        }
    }

    public void tile(HttpServletRequest request, HttpServletResponse response, String layerId, int tileSize, int zoom, int row, int col, String pm) throws IOException {
        //这里分两种情况，1-原始数据，2-算法+数据N个,id格式为c

        //这里需要控制下，对于需要用到同一个tif切片的请求，每次只能放行一个

        String[] arr = layerId.split("-");
        if (arr.length == 1) {
            //原始数据
            originalTile(request, response, zoom, row, col, tileSize, layerId, Long.valueOf(arr[0]));
        } else if (arr.length == 2) {
            Integer algorithmId = Integer.valueOf(arr[0]);
            algorithmTile(request, response, zoom, row, col, tileSize, arr[1], algorithmId, pm);
        }
    }

    private void algorithmTile(HttpServletRequest request, HttpServletResponse response, int zoom, int row, int col, int tileSize, String layerId, Integer algorithmId, String pm) throws IOException {
        JSONObject params = JSONObject.parseObject(pm);
        String dataType = params.getString("type");
        //通过算法ID得到配色方案
        LegendInfo legendInfo = cacheLegend2(Long.valueOf(algorithmId), 2, dataType);
        String tilePath = Path.of(tileProperties.getGrid().getTileDir(), String.valueOf(algorithmId), String.valueOf(legendInfo.getId()), layerId, String.valueOf(zoom), String.valueOf(col), row + "." + ImageType.PNG.getName()).toString();
        File tileFile = new File(tilePath);
        if (!tileFile.exists()) {
            String[] dataIds = layerId.split("_");
            List<byte[]> tifTileBytes = new ArrayList<>();
            List<String> tilePathList = new ArrayList<>();
            for (String id : dataIds) {
                String tifTilePath = Path.of(tileProperties.getGrid().getTifDir(), id, String.valueOf(zoom), String.valueOf(col), row + "." + ImageType.TIFF.getName()).toString();
                Long dataId = Long.parseLong(id);
                GridInfo gridInfo = cacheInfo(dataId);
                if (gridInfo == null) return;
                //本地数据
                GridUtil.tifTile(gridInfo, tifTilePath, tileSize, zoom, row, col);
//                tifTileBytes.add(FileUtils.readBytes(tifTilePath));
                if (FileUtils.exist(tifTilePath)) {
                    tilePathList.add(tifTilePath);
                }
            }
            if (tilePathList.size() != dataIds.length) {
                return;
            }
//            byte[] pmBytes = pm.getBytes();
//            byte[] pmLengthBytes = int2Bytes(pmBytes.length);
//            byte[] allBytes = ArrayUtil.addAll(pmLengthBytes, pmBytes);
//            allBytes = ArrayUtil.addAll(allBytes, int2Bytes(dataIds.length));
//            for (byte[] bytes : tifTileBytes) {
//                byte[] tileLengthBytes = int2Bytes(bytes.length);
//                allBytes = ArrayUtil.addAll(allBytes, tileLengthBytes, bytes);
//            }
            List<Double> resultData = ComputedUtil.start(algorithmId, params, tilePathList);

            if (!resultData.isEmpty() && resultData.size() == tileSize * tileSize) {
                //将算法返回的结果转成png图片并缓存起来
                double[][] newBandArray = GridUtil.resetBandsDataByValueArray(resultData, tileSize, tileSize, legendInfo, 0);
                GridUtil.rgbArray2Image(newBandArray, tileSize, tileSize, tilePath);
            }
        }
        if (tileFile.exists()) {
            FileUtils.downloadFile(request, response, tileFile, false);
        }
    }

    /**
     * 原始影像切片
     *
     * @param request
     * @param response
     * @param zoom
     * @param row
     * @param col
     * @param tileSize
     * @param layerId
     * @param dataId
     * @throws IOException
     */
    private void originalTile(HttpServletRequest request, HttpServletResponse response, int zoom, int row, int col, int tileSize, String layerId, Long dataId) throws IOException {
        //通过ID得到影像信息
        GridInfo gridInfo = cacheInfo(dataId);
        if (gridInfo == null) return;
        //通过原始数据ID得到配色方案
        LegendInfo legendInfo = cacheLegend(dataId, 1);
        //先检查tif切片是否存在
        String tilePath = Path.of(tileProperties.getGrid().getTileDir(), layerId, String.valueOf(legendInfo.getId()), String.valueOf(zoom), String.valueOf(col), row + "." + ImageType.PNG.getName()).toString();
        File tileFile = new File(tilePath);
        if (!tileFile.exists()) {
            //如果tif切片不存在，则生成tif切片
            String tifTilePath = Path.of(tileProperties.getGrid().getTifDir(), String.valueOf(dataId), String.valueOf(zoom), String.valueOf(col), row + "." + ImageType.TIFF.getName()).toString();
            File tifTileFile = new File(tifTilePath);
            //如果tif切片不存在,则生成tif切片
            GridUtil.tifTile(gridInfo, tifTilePath, tileSize, zoom, row, col);
            //tif切片转png
            GridUtil.tif2Png(tifTilePath, tilePath, legendInfo, gridInfo.getMinValue(), gridInfo.getMaxValue());
        }
        FileUtils.downloadFile(request, response, tileFile, !this.tileProperties.getGrid().getCacheable());
    }

    private int[] doubleList2intArray(List<Double> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).intValue();
        }
        return result;
    }


    private void saveByteArrayToFile(byte[] bytes, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(bytes);

    }



}
