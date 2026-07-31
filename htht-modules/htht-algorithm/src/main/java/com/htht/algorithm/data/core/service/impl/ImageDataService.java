package com.htht.algorithm.data.core.service.impl;

import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.core.constants.DataConstants;
import com.htht.algorithm.data.core.service.CommonService;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.tile.grid.entity.LegendInfo;
import com.htht.algorithm.data.tile.grid.service.GridService;
import com.htht.algorithm.data.tile.grid.utils.GridUtil;
import com.htht.algorithm.data.tile.vector.utils.ShpUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Service
public class ImageDataService extends GridService<Long> implements CommonService {

    private static final String[] IMAGE_TYPES = new String[]{"png", "jpeg", "jpg"};
    private IBusFileInfoService fileInfoService;

    private IBusDataInfoService dataInfoService;

    public ImageDataService(TileProperties tileProperties, IBusFileInfoService fileInfoService, IBusDataInfoService dataInfoService) {
        super(tileProperties);
        this.fileInfoService = fileInfoService;
        this.dataInfoService = dataInfoService;
    }

    @Override
    public BusFileInfo upload(MultipartFile file) throws IOException {
        checkFileType(file);
        BusFileInfo fileInfo = fileInfoService.uploadFile(file, tileProperties.getImage().getUploadPath());
        return fileInfo;
    }

    @Override
    @Pooled(executor = "imageStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void storage(BusDataInfo dataInfo) {
        //更改状态为入库中
        updateStock(dataInfo, DataConstants.DATA_STOCK_ING);
        try {
            BusFileInfo fileInfo = fileInfoService.getFileInfoById(dataInfo.getFileId());
            //TODO 栅格数据入库
            if (dataInfo.getLngMin() == null
                || dataInfo.getLngMax() == null
                || dataInfo.getLatMin() == null
                || dataInfo.getLatMax() == null) {
                throw new GlobalException("缺少四角坐标");
            }
            GridUtil.setProjAndTransform(fileInfo.getPath(), dataInfo.getLngMin(), dataInfo.getLngMax(), dataInfo.getLatMin(), dataInfo.getLatMax());
            dataInfo.setExtent(ShpUtil.createWkt(dataInfo.getLngMin(), dataInfo.getLngMax(), dataInfo.getLatMin(), dataInfo.getLatMax()));
            dataInfo.setStock(DataConstants.DATA_STOCK_SUCCESS);
            dataInfoService.updateDataInfo(dataInfo);
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
        if (!Arrays.asList(IMAGE_TYPES).contains(FileUtils.getSuffix(file.getOriginalFilename()))) {
            throw new GlobalException("文件格式不正确，请上传：" + Arrays.toString(IMAGE_TYPES));
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

    @Override
    public LegendInfo getLegendInfo(Long id, int type) {
        return null;
    }

    @Override
    public LegendInfo getLegendInfo2(Long id, String dataType, int type) {
        return null;
    }
}
