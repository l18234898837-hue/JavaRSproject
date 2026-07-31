package com.htht.algorithm.data.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.algorithm.data.utils.dataDownload.s3Download.impl.SentinelS3Downloader;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.mapper.BusRegionInfoMapper;
import com.htht.algorithm.data.domain.BusSentinelInfo;
import com.htht.algorithm.data.mapper.BusSentinelInfoMapper;
import com.htht.algorithm.data.service.IBusSentinelInfoService;
import com.htht.algorithm.data.utils.SentinelUtil;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.gdal.ogr.Geometry;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-07-18
 */
@Service
@RequiredArgsConstructor
public class BusSentinelInfoServiceImpl extends ServiceImpl<BusSentinelInfoMapper, BusSentinelInfo> implements IBusSentinelInfoService {

    private final BusRegionInfoMapper regionInfoMapper;

    private  final StOnlineDownloadInfoService stOnlineDownloadInfoService;

    private final TileProperties tileProperties;

    private final SentinelS3Downloader sentinelS3Downloader;

    @Override
    public int addSentinelInfo(BusSentinelInfo sentinelInfo) {
        if (sentinelInfo.getPid() == 0L) {
            sentinelInfo.setLevel(0);
        } else {
            BusSentinelInfo parentSentinelInfo = Optional.ofNullable(this.getById(sentinelInfo.getPid())).orElseThrow(() -> new GlobalException("父级资源不存在"));
            if (parentSentinelInfo.getLevel() >= 1) {
                throw new GlobalException("资源层级不能大于2级");
            }
            sentinelInfo.setLevel(parentSentinelInfo.getLevel() + 1);
        }
        return baseMapper.insert(sentinelInfo);
    }

    @Override
    public int removeSentinelInfo(Long id) {
        BusSentinelInfo oldSentinelInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("资源不存在"));
        if (oldSentinelInfo.getLevel() == 0) {
            throw new GlobalException("不能删除等级为1的资源");
        }
        return baseMapper.deleteById(id);
    }

    @Override
    public int updateSentinelInfo(BusSentinelInfo sentinelInfo) {
        return baseMapper.updateById(sentinelInfo);
    }

    @Override
    public List<BusSentinelInfo> getSentinelList(BusSentinelInfo sentinelInfo) {
        return this.lambdaQuery()
            .like(Strings.isNotBlank(sentinelInfo.getName()), BusSentinelInfo::getName, sentinelInfo.getName())
            .orderByAsc(BusSentinelInfo::getPid)
            .orderByAsc(BusSentinelInfo::getOrderNum)
            .list();
    }

    @Override
    public List<BusSentinelInfo> getRootSentinelList() {
        return this.lambdaQuery()
            .orderByAsc(BusSentinelInfo::getOrderNum)
            .eq(BusSentinelInfo::getLevel, 0)
            .list();
    }

    @Override
    public List<BusSentinelInfo> buildSentinelTree() {
        List<BusSentinelInfo> sentinelInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, BusSentinelInfo> sentinelMap = sentinelInfoList.stream().collect(
            Collectors.toMap(BusSentinelInfo::getId, Function.identity(), (key1, key2) -> key2)
        );
        List<BusSentinelInfo> sentinelTree = new ArrayList<>();
        sentinelMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getPid() != 0) {
                BusSentinelInfo parentSentinel = sentinelMap.get(entry.getValue().getPid());
                List<BusSentinelInfo> children = parentSentinel.getChildren();
                if (Objects.isNull(children)) {
                    children = new ArrayList<>();
                    parentSentinel.setChildren(children);
                }
                children.add(entry.getValue());
                children.sort(Comparator.comparing(BusSentinelInfo::getOrderNum));
            } else {
                sentinelTree.add(entry.getValue());
            }
        });
        sentinelTree.sort(Comparator.comparing(BusSentinelInfo::getOrderNum));
        return sentinelTree;
    }

    @Override
    public boolean hasChildBySentinelId(Long id) {
        return baseMapper.exists(new LambdaQueryWrapper<BusSentinelInfo>().eq(BusSentinelInfo::getPid, id));
    }

    @Override
    public BusSentinelInfo getSentinelById(Long id) {
        return this.getById(id);
    }

    @Override
    public JSONObject search(String api, String local, Date startTime, Date endTime, String collection, Integer limit, Integer next, Integer cloud, String filter) {
//        System.out.println("进入search方法");
        JSONObject localObj = JSONObject.parseObject(local);
//        System.out.println("成功获取json对象");
        Integer type = localObj.getInteger("type");
        JSONObject value = localObj.getJSONObject("value");
        String intersects = null;
        if (type == 1 || type == 3) {
            //地图选择和经纬度
            String valueType = value.getString("type");
            if(!"no".equals(valueType)){
                if ("circle".equals(valueType)) {
                    //如果为圆形，需要单独处理

                } else {
                    String wkt = value.getString("wkt");
                    intersects = Geometry.CreateFromWkt(wkt).ExportToJson();
                }
            }
        } else if (type == 2) {
            //行政区
            String prov = value.getString("prov");
            String city = value.getString("city");

            if (!"0".equals(prov)) {
                if (!"0".equals(city)) {
                    //指定市
                    intersects = Geometry.CreateFromWkt(regionInfoMapper.getGeometryByCode(city)).ExportToJson();
                } else {
                    //指定省
                    intersects = Geometry.CreateFromWkt(regionInfoMapper.getGeometryByCode(prov)).ExportToJson();
                }
            }
        }
//        System.out.println("成功进行条件判断");
        String result = SentinelUtil.search(api, intersects, startTime, endTime, collection, limit, next, getFilter(cloud, filter));
        JSONObject jsonObj = JSONObject.parseObject(result);
        JSONArray features = jsonObj.getJSONArray("features");
        for (int i = 0; i < features.size(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            JSONArray bbox = feature.getJSONArray("bbox");
            for (int k = 0; k < bbox.size(); k++) {
                bbox.set(k, Double.parseDouble(bbox.getString(k)));
            }
            for (int j = 0; j < coordinates.size(); j++) {
                for (int k = 0; k < coordinates.getJSONArray(j).size(); k++) {
                    for (int m = 0; m < coordinates.getJSONArray(j).getJSONArray(k).size(); m++) {
                        if (geometry.getString("type").equals("Polygon")) {
                            String val = coordinates.getJSONArray(j).getJSONArray(k).getString(m);
                            coordinates.getJSONArray(j).getJSONArray(k).set(m, Double.parseDouble(val));
                        } else {
                            for (int n = 0; n < coordinates.getJSONArray(j).getJSONArray(k).getJSONArray(m).size(); n++) {
                                String val = coordinates.getJSONArray(j).getJSONArray(k).getJSONArray(m).getString(n);
                                coordinates.getJSONArray(j).getJSONArray(k).getJSONArray(m).set(n, Double.parseDouble(val));
                            }
                        }
                    }
                }
            }
        }
//        System.out.println("处理完成，返回数据");
        return jsonObj;
    }

    private String getFilter(Integer cloud, String filter) {
        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(cloud)) {
            sb.append("eo:cloud_cover<=" + cloud);
        }
        if (Strings.isNotBlank(filter)) {
            sb.append(Objects.nonNull(cloud) ? " and " : "");
            sb.append(filter);
        }
        return Strings.isNotBlank(sb.toString()) ? sb.toString() : null;
    }

    @Override
    public List<BusSentinelInfo> getLayerListByPid(Long pid) {
        return this.lambdaQuery()
            .eq(BusSentinelInfo::getPid, pid)
            .orderByAsc(BusSentinelInfo::getId)
            .list();
    }

    @Override
    public void tile(HttpServletRequest request, HttpServletResponse response, String id, String api, String instanceId, String layerId, String format, int tileMatrix, int tileRow, int tileCol, String startTime, String endTime) throws Exception {
        String imagePath = tileProperties.getGrid().getTileDir() + "/" + id + "/" + layerId + "/" + tileMatrix + "/" + tileCol + "/" + tileRow + "." + format;
        File imageFile = new File(imagePath);
        InputStream in = null;
        if (!imageFile.exists()) {
            in = SentinelUtil.getTile(api, instanceId, layerId, "image/" + format, tileMatrix, tileRow, tileCol, startTime, endTime);
            imageFile.getParentFile().mkdirs();
            imageFile.createNewFile();
            saveImage(imagePath, in);
        }
        FileUtils.downloadFile(request, response, imageFile, false);
    }

    @Override
    public boolean hasAllBandLayer(Long id) {
        return this.lambdaQuery()
            .eq(BusSentinelInfo::getPid, id)
            .eq(BusSentinelInfo::getName, "ALL BAND")
            .exists();
    }

    @Override
    public BusSentinelInfo getAllBandLayer(Long id) {
        return this.lambdaQuery()
            .eq(BusSentinelInfo::getPid,id)
            .eq(BusSentinelInfo::getName,"ALL BAND")
            .one();
    }

    @Override
    public void downloadSentinelData(String name,String s3Url) {
        boolean isS3Start = s3Url.startsWith("s3://");
        if (isS3Start) {
            s3Url = s3Url.substring(5);
        }
        // 找到第一个 '/' 的位置
        int firstSlashIndex = s3Url.indexOf('/');
        String part1,part2;
        if (firstSlashIndex != -1) {
            // 分割字符串
            part1 = s3Url.substring(0, firstSlashIndex);  // "sentinel-s2-l1c"
            part2 = s3Url.substring(firstSlashIndex + 1); // "tiles/50/T/MK/2025/8/4/0/"
        } else {
            throw new GlobalException("输入正确s3库字符串？");
        }
        String onlineDir = tileProperties.getVector().getOnlineDir();
        Path path = Paths.get(onlineDir, s3Url);
        StOnlineDownloadInfo stOnlineDownloadInfo = new StOnlineDownloadInfo().
            setName(name).
            setDownloadUrl(path.toString()).
            setS3Url(s3Url).
            setStatus((short) 0).
            setStoreStatus((short) 0).
            setDataType(1);
        stOnlineDownloadInfoService.save(stOnlineDownloadInfo);
        sentinelS3Downloader.downloadSentinelData(part1, part2, path.toString(),stOnlineDownloadInfo);
        stOnlineDownloadInfo.setStatus((short) 1);
//        System.out.println("开始下载数据");
    }

    private void saveImage(String path, InputStream in) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(path));
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = in.read(bytes)) != -1) {
                outStream.write(bytes, 0, len);
            }
            out.write(outStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }
}
