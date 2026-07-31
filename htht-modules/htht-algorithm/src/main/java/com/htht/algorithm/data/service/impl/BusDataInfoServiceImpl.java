package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.*;
import com.htht.algorithm.data.domain.dto.SatelliteDto;
import com.htht.algorithm.data.domain.vo.BusDataInfoDownloadVo;
import com.htht.algorithm.data.domain.vo.BusDataInfoVo;
import com.htht.algorithm.data.enums.L2NcFileType;
import com.htht.algorithm.data.enums.NcFileType;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.algorithm.data.service.*;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.FileZipper;
import com.htht.algorithm.data.utils.dataDownload.DownloadUserService;
import com.htht.algorithm.data.utils.shp.GeoToolsUtil;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.algorithm.data.constants.DataConstants;
import com.htht.algorithm.data.enums.FileType;
import com.htht.algorithm.data.core.service.impl.GridDataService;
import com.htht.algorithm.data.core.service.impl.ImageDataService;
import com.htht.algorithm.data.core.service.impl.VectorDataService;
import com.htht.algorithm.data.domain.dto.TransformDTO;
import com.htht.algorithm.data.mapper.BusDataInfoMapper;
import com.htht.algorithm.data.utils.GeoTools;
import com.htht.algorithm.data.tile.grid.utils.GridUtil;
import com.htht.common.exception.ServiceException;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.file.FileUtils;
import com.htht.common.utils.poi.ExcelUtil;
import jodd.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
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
@Slf4j
public class BusDataInfoServiceImpl extends ServiceImpl<BusDataInfoMapper, BusDataInfo> implements IBusDataInfoService {

    private final IBusFileInfoService fileInfoService;

    @Autowired
    @Lazy
    private GridDataService gridDataService;

    @Autowired
    @Lazy
    private VectorDataService shpDataService;

    @Autowired
    @Lazy
    private ImageDataService imageDataService;

    @Autowired
    @Lazy
    private IBusCatalogDataInfoService catalogDataInfoService;

    @Lazy
    @Autowired
    private StUserCatalogDataInfoService stUserCatalogDataInfoService;

    @Lazy
    @Autowired
    private StDataTypeService dataTypeService;

    private final SdColorRampService sdColorRampService;

    private final DownloadUserService downloadUserService;

    private final IBusFeatureFieldInfoService featureFieldInfoService;
    private final TileProperties tileProperties;

    private static final String[] Other_TYPES = new String[]{"nc", "zip"};

    @Override
    public Long uploadOtherFile(MultipartFile file) throws IOException {
        return fileInfoService.uploadFile(file, tileProperties.getGrid().getUploadPath()).getId();
    }

    @Override
    public void checkFileType(MultipartFile file) {
        if (!Arrays.asList(Other_TYPES).contains(FileUtils.getSuffix(file.getOriginalFilename()))) {
            throw new GlobalException("文件格式不正确，请上传：" + Arrays.toString(Other_TYPES));
        }
    }

    @Override
    public Long uploadGridFile(MultipartFile file) throws IOException {
        return gridDataService.upload(file).getId();
    }

    @Override
    public Long uploadImageFile(MultipartFile file) throws IOException {
        return imageDataService.upload(file).getId();
    }

    @Override
    public Long uploadShpFile(MultipartFile file) throws IOException {
        return shpDataService.upload(file).getId();
    }


    @Override
    public int insertDataInfo(BusDataInfo dataInfo) {
        if (dataInfo.getStatus() == null) {
            dataInfo.setStatus(DataConstants.DATA_NORMAL);
        }
        dataInfo.setStock(DataConstants.DATA_UN_STOCK);
        return baseMapper.insert(dataInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = DataConstants.DATA_GRID_CACHE_NAME, key = "#id")
    public int removeDataInfo(Long id) {
        BusDataInfo dataInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据资源不存在：" + id));
        //移除资源目录绑定的资源
        catalogDataInfoService.removeCatalogDataInfoByDataId(id);
        stUserCatalogDataInfoService.removeByUserIdAndDataId(id);
        if (dataInfo.getFileId() != null) {
            fileInfoService.removeFile(dataInfo.getFileId());
        }
        if (dataInfo.getTransformPath() != null) {
            FileUtils.deleteFile(dataInfo.getTransformPath());
        }
        if (dataInfo.getDisplayThumbnail() != null) {
            FileUtils.deleteFile(dataInfo.getDisplayThumbnail());
        }
        if (dataInfo.getDownloadThumbnail() != null) {
            FileUtils.deleteFile(dataInfo.getDownloadThumbnail());
        }
        //如果数据为矢量数据，移除字段记录
        if (dataInfo.getType() == FileType.SHP.getType()) {
            featureFieldInfoService.removeFieldByDataId(id);
            if (dataInfo.getStock() == DataConstants.DATA_STOCK_SUCCESS) {
                GeoTools.deletePgTable("shp_" + dataInfo.getId());
            }
        }
        return baseMapper.deleteById(id);
    }

    @Override
    @CachePut(cacheNames = DataConstants.DATA_GRID_CACHE_NAME, key = "#dataInfo.id")
    public BusDataInfo updateDataInfo(BusDataInfo dataInfo) {
        baseMapper.updateById(dataInfo);
        return dataInfo;
    }

    @Override
    public TableDataInfo<BusDataInfo> selectPage(BusDataInfo dataInfo, PageQuery pageQuery) {
        LambdaQueryChainWrapper<BusDataInfo> wrapper = buildQueryWrapper(dataInfo);
        /*if (params.get("startTime") != null && params.get("endTime") != null) {
            wrapper.between(BusDataInfo::getProductTime, DateUtil.parse((String) params.get("startTime"), "yyyy-MM-dd"), DateUtil.parse((String) params.get("endTime"), "yyyy-MM-dd"));
        }*/
        Page<BusDataInfo> page = wrapper
            .orderByDesc(BusDataInfo::getCreateTime)
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()));
        return TableDataInfo.build(page);
    }

    @Override
    public void storage(Long id) {
        BusDataInfo dataInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据资源不存在：" + id));
        if (Objects.equals(dataInfo.getStock(), DataConstants.DATA_STOCK_SUCCESS)) {
            throw new GlobalException("资源数据已入库");
        }
        FileType fileType = FileType.getByType(dataInfo.getType());
        if (fileType == FileType.GEO_TIFF) {
            gridDataService.storage(dataInfo);
        } else if (fileType == FileType.SHP) {
            shpDataService.storage(dataInfo);
        } else if (fileType == FileType.GRID_ZIP) {
            gridDataService.storageZip(dataInfo);
        } else if (fileType ==FileType.NC){
            gridDataService.storageNC(dataInfo);
        }
    }

    @Override
    public void storageAll() {
        List<BusDataInfo> list = list();
        for (BusDataInfo busDataInfo : list) {
            if ((busDataInfo.getStock().equals("0"))
            ) {
                storage(busDataInfo.getId());
            }
        }
    }

    @Override
    public Boolean checkNameUnique(BusDataInfo dataInfo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BusDataInfo>()
            .eq(BusDataInfo::getName, dataInfo.getName())
            .ne(ObjectUtil.isNotNull(dataInfo.getId()), BusDataInfo::getId, dataInfo.getId()));
        return !exist;
    }

    @Override
    @Cacheable(cacheNames = DataConstants.DATA_GRID_CACHE_NAME, key = "#id")
    public BusDataInfo getDataInfoById(Long id) {
        return Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据资源不存在"));
    }

    @Override
    public void download(HttpServletResponse response, Long id) throws IOException {
        BusDataInfo dataInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("数据资源不存在"));
        String suffixStr = "";
        if (dataInfo.getType() == 1) {
            suffixStr = "grid";
        } else if (dataInfo.getType() == 2) {
            suffixStr = "vector";
        }
        fileInfoService.download(response, dataInfo.getFileId(), tileProperties.getBasePath());
    }

    @Override
    public void download(List<StDownloadLogEvent> downloadLogEvents, BusDataInfoDownloadVo dataInfoDownloadVo, HttpServletResponse response) throws IOException {
        long currentTimestamp = System.currentTimeMillis();
        downloadLogEvents.forEach(event -> event.setRecordId(currentTimestamp));
        List<FileZipper.FileInfo> fileInfos = new ArrayList<>();
        for (Long dataId : dataInfoDownloadVo.getDataIds()) {
            addZip(dataId, fileInfos);
        }
        String downZipDir = Path.of(tileProperties.getVector().getDownZipDir(), currentTimestamp + ".zip").toString();
        FileZipper.zipFiles(fileInfos, downZipDir);
        fileInfoService.download(response, downZipDir, tileProperties.getBasePath(), dataInfoDownloadVo.getName() + ".zip");


    }

    public void addZip(Long dataId, List<FileZipper.FileInfo> fileInfos) {
        isHaveThumb(dataId);
        BusDataInfo dataInfo = getById(dataId);
        BusFileInfo fileInfo = fileInfoService.getById(dataInfo.getFileId());
        fileInfos.add(new FileZipper.FileInfo(fileInfo.getPath(), fileInfo.getFilename() + "." + fileInfo.getExtension()));
        File file = new File(dataInfo.getDownloadThumbnail());
        String extension = FileStrUtil.getExtension(file);
        fileInfos.add(new FileZipper.FileInfo(dataInfo.getDownloadThumbnail(), fileInfo.getFilename() + "." + extension));
    }


    /**
     * 兼容之前的检测办法
     * @param dataId
     */
    public void isHaveThumb(Long dataId) {
            isHaveThumb(dataId,null,false);
    }


    public void isHaveThumb(Long dataId,Long sdColorRampId,boolean updateThumbnail) {
        BusDataInfo dataInfo = getById(dataId);
        String displayThumbnailPath = dataInfo.getDisplayThumbnail();
        String downloadThumbnail = dataInfo.getDownloadThumbnail();
        if (StringUtil.isBlank(displayThumbnailPath) || StringUtil.isBlank(downloadThumbnail)|| updateThumbnail) {
            displayThumbnailPath = Path.of(tileProperties.getGrid().getThumbPath(), dataId + ".jpg").toString();
            L2NcFileType ncFileType = L2NcFileType.isNcFileType(dataInfo.getName());
            String path = dataInfo.getTransformPath();
            if(ncFileType==null){
                if(sdColorRampId==null){
                    GridUtil.createThumb(path, displayThumbnailPath,sdColorRampService.getDefaultColorRamp());
                }else{
                    GridUtil.createThumb(path, displayThumbnailPath,sdColorRampService.getColorRampById(sdColorRampId));
                }
            }else{
                if(sdColorRampId==null){
                    try {
                        L2NcFileType.createThumbByJson(path, displayThumbnailPath,ncFileType,sdColorRampService.getDefaultColorRamp());
                    } catch (Exception e) {
                        throw new GlobalException(e.getMessage());
                    }
                }else{
                    try {
                        L2NcFileType.createThumbByJson(path, displayThumbnailPath,ncFileType,sdColorRampService.getColorRampById(sdColorRampId));
                    } catch (Exception e) {
                        throw new GlobalException(e.getMessage());
                    }
                }
            }
            dataInfo.setDisplayThumbnail(displayThumbnailPath)
                .setDownloadThumbnail(displayThumbnailPath);
            updateById(dataInfo);
        }
    }

    @Override
    public List<StDownloadLogEvent> checkDownloadPermission(List<Long> dataIds) {
        List<StDownloadLogEvent> logEventList = new ArrayList<>();
        Set<String> levelSet = new HashSet<>();
        for (Long dataId : dataIds) {
            StDownloadLogEvent stDownloadLogEvent = new StDownloadLogEvent();
            levelSet.addAll(checkDownloadPermission(dataId, stDownloadLogEvent));
            logEventList.add(stDownloadLogEvent);
        }
        List<String> notPerStrList=new ArrayList<>();
        for (String levelStr : levelSet) {
            boolean havePer = downloadUserService.isHavePer(levelStr);
            if (!havePer){
                notPerStrList.add(levelStr);
            }
        }
        if(!notPerStrList.isEmpty()&&!LoginHelper.isAdmin()){
            throw new GlobalException("您没有以下权限："+notPerStrList);
        }
        return logEventList;
    }

    @Override
    public Set<String> checkDownloadPermission(Long id, StDownloadLogEvent logEvent) {
        Long userId = LoginHelper.getUserId();
        //获取用户目录下这个数据是否有绑定目录
        Set<Long> userCatalogIdSet = stUserCatalogDataInfoService.list(new LambdaQueryWrapper<StUserCatalogDataInfo>()
                .eq(StUserCatalogDataInfo::getUserId, userId)
                .eq(StUserCatalogDataInfo::getDataId, id)).stream().
            map(StUserCatalogDataInfo::getCatalogId)
            .collect(Collectors.toSet());

        //获取公共数据集是否有绑定目录
        Set<Long> catalogIdSet = catalogDataInfoService.list(new LambdaQueryWrapper<BusCatalogDataInfo>()
                .eq(BusCatalogDataInfo::getDataId, id))
            .stream().map(BusCatalogDataInfo::getCatalogId)
            .collect(Collectors.toSet());

        //合并获取到的目录id
        catalogIdSet.addAll(userCatalogIdSet);
        //从里面获取到的所有目录id去获取相关类型，并且检查能否使用
        Set<String> levelSet = new HashSet<>();
        for (Long datalogId : catalogIdSet) {
            List<StDataType> dataTypeByproductCatalogIdOrCalculateCatalogId = dataTypeService.getDataTypeByproductCatalogIdOrCalculateCatalogId(datalogId);
            Set<String> collect = dataTypeByproductCatalogIdOrCalculateCatalogId
                .stream().map(StDataType::getName).collect(Collectors.toSet());
            for (String name : collect) {
                levelSet.add(name.substring(0, 2));
            }
        }
        for (Long datalogId : catalogIdSet) {
            List<StDataType> dataTypeByproductCatalogIdOrCalculateCatalogId = dataTypeService.getDataTypeByproductCatalogIdOrCalculateCatalogId(datalogId);
            for (StDataType dataType : dataTypeByproductCatalogIdOrCalculateCatalogId) {
                StDataType parent = dataTypeService.getParent(dataType.getPid());
                BusDataInfo dataInfo = getById(id);
                BusFileInfo fileInfo = fileInfoService.getById(dataInfo.getFileId());
                logEvent.setUserId(userId);
                logEvent.setDataName(dataInfo.getName());
                logEvent.setDataType(fileInfo.getExtension());
                logEvent.setSatelliteSensor(parent.getName());
                logEvent.setDataLevel(dataType.getName());
                logEvent.setDownloadLocation((short) 1);
                logEvent.setDownloadStatus((short) 1);
            }
        }
        return levelSet;
    }


    @Override
    public boolean checkDownloadLevel(Long id, String dataLevel) {
//        //获取当前下载用户id
//        Long userId = LoginHelper.getUserId();
//        Set<Long> userCatalogIdSet = stUserCatalogDataInfoService.list(new LambdaQueryWrapper<StUserCatalogDataInfo>()
//                .eq(StUserCatalogDataInfo::getUserId, userId)
//                .eq(StUserCatalogDataInfo::getDataId, id)).stream().
//            map(StUserCatalogDataInfo::getCatalogId)
//            .collect(Collectors.toSet());
//
//        Set<Long> catalogIdSet = catalogDataInfoService.list(new LambdaQueryWrapper<BusCatalogDataInfo>()
//                .eq(BusCatalogDataInfo::getDataId, id))
//            .stream().map(BusCatalogDataInfo::getCatalogId)
//            .collect(Collectors.toSet());
//
//        catalogIdSet.addAll(userCatalogIdSet);
//
//        for (Long datalogId : catalogIdSet) {
//            Set<String> collect = dataTypeService.getDataTypeByproductCatalogIdOrCalculateCatalogId(datalogId)
//                .stream().map(StDataType::getName).collect(Collectors.toSet());
//            for (String name : collect) {
//                if(name.contains(dataLevel)){
//                    return true;
//                }
//            }
//        }
        return false;

    }

    @Override
    public void checkDownloadPre() {
        if (downloadUserService.isUserDownloading()) {
            throw new GlobalException("用户正在下载数据");
        }
        if (downloadUserService.isBatchPre()) {
            downloadUserService.addUserToDownloading();
        }

    }

    @Override
    public void clearDownloadStatus() {
        downloadUserService.removeUserFromDownloading();
    }


    @Override
    public Page<BusDataInfo> pageByIds(Page<BusDataInfo> page, List<Long> dataIds) {
        // 使用 lambda 表达式进行查询
        return this.lambdaQuery()
            .in(BusDataInfo::getId, dataIds)
            .orderByDesc(BusDataInfo::getProductTime) // 按照产品时间降序排序
            .page(page);
    }


    @Override
    public TableDataInfo<BusDataInfo> getDataInfoListByCondition(JSONObject bo, PageQuery pageQuery) {
        Page<BusDataInfo> page = Page.of(pageQuery.getPageNum(), pageQuery.getPageSize());
        this.baseMapper.getDataInfoListByCondition(page, bo);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<BusDataInfo> getDataInfoListByConditionAndUser(JSONObject bo, PageQuery pageQuery) {
        Page<BusDataInfo> page = Page.of(pageQuery.getPageNum(), pageQuery.getPageSize());
        this.baseMapper.getDataInfoListByConditionAndUser(page, bo);
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<BusDataInfo> getDataInfoListByConditionAndAll(JSONObject bo, PageQuery pageQuery) {
        Page<BusDataInfo> page = Page.of(pageQuery.getPageNum(), pageQuery.getPageSize());
        this.baseMapper.getDataInfoListByConditionAndAll(page, bo);
        return TableDataInfo.build(page);
    }

    @Override
    public Boolean checkDataIntersects(Long id1, Long id2) {
        return this.baseMapper.checkDataIntersects(id1, id2);
    }

    @Override
    public List<Map<String, Object>> statistic(Long id, String statsField, String groupField, String oneField, String twoField) {
        return this.baseMapper.statistic(id, statsField, groupField, oneField, twoField);
    }

    @Override
    public double[][] transformCoordinates(TransformDTO dto) {
        return GridUtil.transformCoordinates(dto.getCoordinates(), dto.getEpsg());
    }

    @Override
    public void scan() {
        String scanPath = tileProperties.getScanPath();
        File scanFile = new File(scanPath);
        if (!scanFile.exists()) {
            throw new ServiceException("扫描文件夹不存在:" + scanPath);
        }
        File[] files = scanFile.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".zip") || name.endsWith(".tif") || name.endsWith(".tiff");
            }
        });

        for (File file : files) {
            String filename = file.getName();
            log.info("--------------扫描入库开始：{}-------------", filename);
            if (filename.endsWith(".zip")) {
                //矢量文件
                scanVector(file);
            } else if (filename.endsWith(".tif") || filename.endsWith(".tiff")) {
                //栅格文件
                scanGrid(file);
            }
            log.info("--------------扫描入库结束：{}-------------", filename);
        }
    }


    @Override
    public void downloadThumbJpg(HttpServletResponse response, Long id) throws IOException {
        isHaveThumb(id);
        fileInfoService.downloadThumbJpg(response, getById(id).getDisplayThumbnail(), tileProperties.getBasePath());
    }

    @Override
    public void downloadThumbJpg(HttpServletResponse response, Long id, Long colorRampId) throws IOException {
        isHaveThumb(id, colorRampId,true);
        fileInfoService.downloadThumbJpg(response, getById(id).getDisplayThumbnail(), tileProperties.getBasePath());
    }

    @Override
    public void injectValueSatelliteValue(List<BusDataInfo> values) {
        Map<Long, List<BusDataInfo>> groupByCatalogId = values.stream().collect(Collectors.groupingBy(BusDataInfo::getCatalogId));
        Map<Long, SatelliteDto> satelliteDtoMap = groupByCatalogId.keySet().stream()
            .collect(Collectors.toMap(
                // key 提取器：直接用 catalogId 作为 Map 的 key（Function.identity() 表示“自身”）
                Function.identity(),
                // value 提取器：根据 catalogId 生成 SatelliteDto（核心业务逻辑）
                catalogId -> {
                    List<StDataType> list = dataTypeService.getDataTypeByproductCatalogIdOrCalculateCatalogId(catalogId);
                    SatelliteDto satelliteDto = null; // 外部声明，避免作用域问题
                    if (!list.isEmpty()) {
                        StDataType dataType = list.get(0);
                        StDataType parentType = dataTypeService.getById(dataType.getPid());
                        // 赋值（注意：parentType 可能为 null，建议加非空判断，避免 NPE）
                        if (Objects.nonNull(parentType)) {
                            satelliteDto = new SatelliteDto()
                                .setSatelliteName(parentType.getName())
                                .setSatelliteLevel(dataType.getName());
                        }
                    }
                    return satelliteDto; // 无数据时返回 null
                }
            ));
        for (BusDataInfo value : values) {
            SatelliteDto satelliteDto = satelliteDtoMap.get(value.getCatalogId());
            if (satelliteDto != null) {
                value.setSatelliteName(satelliteDto.getSatelliteName());
                value.setSatelliteLevel(satelliteDto.getSatelliteLevel());
            }
        }

    }


    @Override
    public String getShpGeoJson(Long id) {
        BusDataInfo dataInfo = getDataInfoById(id);
        String shpZipFile = shpDataService.getShpZipFile(dataInfo);
        return GeoToolsUtil.shp2GeoJson(new File(shpZipFile), StandardCharsets.UTF_8);
    }

    @Override
    public BusDataInfo importTiffFile(File file) {
        return scanGrid(file);
    }

    private LambdaQueryChainWrapper<BusDataInfo> buildQueryWrapper(BusDataInfo dataInfo) {
        if (Objects.nonNull(dataInfo.getType()) && dataInfo.getType() == 0) {
            dataInfo.setType(null);
        }
        Map<String, Object> params = dataInfo.getParams();
        LambdaQueryChainWrapper<BusDataInfo> wrapper = this.lambdaQuery()
            .like(Strings.isNotBlank(dataInfo.getName()), BusDataInfo::getName, dataInfo.getName())
            .like(Strings.isNotBlank(dataInfo.getKeywords()), BusDataInfo::getKeywords, dataInfo.getKeywords())
            .eq(Objects.nonNull(dataInfo.getType()), BusDataInfo::getType, dataInfo.getType())
            .eq(Objects.nonNull(dataInfo.getStock()), BusDataInfo::getStock, dataInfo.getStock());
        if (params.get("startTime") != null && Strings.isNotBlank((String) params.get("startTime"))) {
            wrapper.gt(BusDataInfo::getProductTime, DateUtil.parse((String) params.get("startTime"), "yyyy-MM-dd"));
        }
        if (params.get("endTime") != null && Strings.isNotBlank((String) params.get("endTime"))) {
            wrapper.lt(BusDataInfo::getProductTime, DateUtil.parse((String) params.get("endTime"), "yyyy-MM-dd"));
        }
        return wrapper;
    }

    @Override
    public void export(BusDataInfo dataInfo, HttpServletResponse response) {

        LambdaQueryChainWrapper<BusDataInfo> wrapper = buildQueryWrapper(dataInfo);
        List<BusDataInfo> dataInfoList = wrapper
            .orderByDesc(BusDataInfo::getProductTime)
            .list();
        List<BusDataInfoVo> listVo = BeanUtil.copyToList(dataInfoList, BusDataInfoVo.class);
        for (int i = 0; i < listVo.size(); i++) {
            BusDataInfoVo vo = listVo.get(i);
            String stock = dataInfoList.get(i).getStock();
            String status = dataInfoList.get(i).getStatus();
            if (Strings.isNotBlank(stock)) {
                vo.setStock(stock.equals("0") ? "未入库" : (stock.equals("1") ? "已入库" : "入库失败"));
            }

            if (Strings.isNotBlank(status)) {
                vo.setStatus(status.equals("0") ? "停用" : "启用");
            }
        }
        ExcelUtil.exportExcel(listVo, "资源数据", BusDataInfoVo.class, response);
    }


    private void scanVector(File file) {
        String prefix = FileUtils.getPrefix(file.getName());
        BusFileInfo fileInfo = fileInfoService.scan(file, tileProperties.getVector().getUploadPath());
        BusDataInfo dataInfo = new BusDataInfo();
        dataInfo.setKeywords(prefix);
        dataInfo.setProductTime(new Date());
        dataInfo.setActualTime(new Date());
        dataInfo.setStock(DataConstants.DATA_UN_STOCK);
        dataInfo.setStatus("1");
        dataInfo.setFileId(fileInfo.getId());
        dataInfo.setName(prefix);
        dataInfo.setType(DataConstants.DATA_TYPE_VECTOR);
        dataInfo.setRemark(prefix);
        this.save(dataInfo);
    }

    private BusDataInfo scanGrid(File file) {
        String prefix = FileUtils.getPrefix(file.getName());
        BusFileInfo fileInfo = fileInfoService.scan(file, tileProperties.getGrid().getUploadPath());
        BusDataInfo dataInfo = new BusDataInfo();
        dataInfo.setKeywords(prefix);
        dataInfo.setProductTime(new Date());
        dataInfo.setActualTime(new Date());
        dataInfo.setStock(DataConstants.DATA_UN_STOCK);
        dataInfo.setStatus("1");
        dataInfo.setFileId(fileInfo.getId());
        dataInfo.setName(prefix);
        dataInfo.setType(DataConstants.DATA_TYPE_GRID);
        dataInfo.setRemark(prefix);
        this.save(dataInfo);
        return dataInfo;
    }
}
