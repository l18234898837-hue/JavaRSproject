package com.htht.algorithm.data.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.BusDataInfoDownloadVo;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.dto.TransformDTO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
public interface IBusDataInfoService extends IService<BusDataInfo> {

    /**
     * 上传后面系统扩展的文件
     * 类型5的栅格压缩包格式
     * 类型6的nc文件格式
     */
    Long uploadOtherFile(MultipartFile file) throws IOException;

    /**
     * 把对应的检查文件也携带进来
     * @param file
     */
    void checkFileType(MultipartFile file);

    /**
     * 上传geotiff文件
     *
     * @param file
     * @return
     * @throws IOException
     */
    Long uploadGridFile(MultipartFile file) throws IOException;

    /**
     * 上传图片文件
     *
     * @param file
     * @return
     * @throws IOException
     */
    Long uploadImageFile(MultipartFile file) throws IOException;

    /**
     * 上传shp文件
     *
     * @param file
     * @return
     * @throws IOException
     */
    Long uploadShpFile(MultipartFile file) throws IOException;

    /**
     * 添加数据资源
     *
     * @param dataInfo
     */
    int insertDataInfo(BusDataInfo dataInfo);

    /**
     * 删除数据资源
     *
     * @param id
     */
    int removeDataInfo(Long id);

    /**
     * 停用数据资源
     *
     * @param dataInfo
     */
    BusDataInfo updateDataInfo(BusDataInfo dataInfo);


    /**
     * 根据资源名称或关键词和资源时间分页查询资源数据
     *
     * @param dataInfo
     * @param page
     * @return
     */
    TableDataInfo<BusDataInfo> selectPage(BusDataInfo dataInfo, PageQuery page);

    /**
     * 资源数据入库
     *
     * @param id
     */
    void storage(Long id);

    /**
     * 一键入库，把所有的入库文件都执行入库逻辑，只执行tif的
     */
    void storageAll();

    /**
     * 检查资源数据名称是否唯一
     *
     * @param dataInfo
     * @return
     */
    Boolean checkNameUnique(BusDataInfo dataInfo);

    /**
     * 根据ID得到资源信息
     *
     * @param id
     * @return
     */
    BusDataInfo getDataInfoById(Long id);


    /**
     * 数据资源下载
     * @param response
     * @param id
     */
    void download(HttpServletResponse response, Long id) throws  IOException;

    void download(List<StDownloadLogEvent> downloadLogEvents, BusDataInfoDownloadVo dataInfoDownloadVo, HttpServletResponse response) throws IOException;

    List<StDownloadLogEvent> checkDownloadPermission(List<Long> dataIds);

    Set<String> checkDownloadPermission(Long id, StDownloadLogEvent logEvent);

    /**
     * 下载资源认证是否是L几数据
     */
    boolean checkDownloadLevel(Long id,String dataLevel);

    /**     * 检查批量下载权限并且记录下载状态
     */
    void checkDownloadPre();

    /**
     * 清空数据下载状态
     */
    void clearDownloadStatus();


    /**
     * 根据数据ID列表进行分页查询
     * @param page 分页对象
     * @param dataIds 数据ID列表
     * @return 分页结果
     */
    Page<BusDataInfo> pageByIds(Page<BusDataInfo> page, List<Long> dataIds);



    /**
     * @param bo
     * @param pageQuery
     * @return
     */
    TableDataInfo<BusDataInfo> getDataInfoListByCondition(JSONObject bo, PageQuery pageQuery);

    /**
     * @param bo
     * @param pageQuery
     * @return
     */
    TableDataInfo<BusDataInfo> getDataInfoListByConditionAndUser(JSONObject bo, PageQuery pageQuery);

    /**
     * @param bo
     * @param pageQuery
     * @return
     */
    TableDataInfo<BusDataInfo> getDataInfoListByConditionAndAll(JSONObject bo, PageQuery pageQuery);



    /**
     * 检查两景影像是否存在交集
     *
     * @param id1
     * @param id2
     * @return
     */
    Boolean checkDataIntersects(Long id1, Long id2);

    /**
     * 统计
     *
     * @param id
     * @param groupField
     * @param oneField
     * @param twoField
     * @return
     */
    List<Map<String, Object>> statistic(Long id, String statsField, String groupField, String oneField, String twoField);

    /**
     * 将指定坐标系的bbox转换成经纬度坐标系
     *
     * @param dto
     * @return
     */
    double[][] transformCoordinates(TransformDTO dto);

    /**
     * 扫描入库
     */
    void scan();

    /**
     * 将shp文件数据转化成GeoJSon
     */
    String getShpGeoJson(Long id);

    /**
     *  传入本地Tiff文件入库
     */
    BusDataInfo importTiffFile(File file);

    /**
     * 导出
     *
     * @param dataInfo
     */
    void export(BusDataInfo dataInfo, HttpServletResponse response);

    void downloadThumbJpg(HttpServletResponse response, Long id) throws IOException;

    void downloadThumbJpg(HttpServletResponse response, Long id ,Long colorRampId) throws IOException;

    void injectValueSatelliteValue(List<BusDataInfo> values);
}
