package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.exception.GlobalException;
import com.htht.common.exception.ServiceException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;
import com.htht.algorithm.data.domain.BusLegendInfo;
import com.htht.algorithm.data.mapper.BusLegendInfoMapper;
import com.htht.algorithm.data.service.IBusAlgorithmInfoService;
import com.htht.algorithm.data.service.IBusLegendInfoService;
import com.htht.algorithm.data.core.service.impl.GridDataService;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.algorithm.data.config.TileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-06-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusLegendInfoServiceImpl extends ServiceImpl<BusLegendInfoMapper, BusLegendInfo> implements IBusLegendInfoService {

    private final IBusDataInfoService dataInfoService;
    private final IBusAlgorithmInfoService algorithmInfoService;
    private final TileProperties tileProperties;
    private final IBusFileInfoService fileInfoService;
    @Autowired
    @Lazy
    private GridDataService gridDataService;

    @Override
    public int addLegend(BusLegendInfo legendInfo) {
        checkLegend(legendInfo);
        int count = this.baseMapper.insert(legendInfo);
        if (count > 0) {
            gridDataService.clearLegendCacheById(legendInfo.getType() == 1 ? legendInfo.getDataId() : legendInfo.getAlgorithmId());
        }
        return count;
    }

    @Override
    public int removeLegend(Long id) {
        BusLegendInfo oldLegend = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("配色不存在"));
        int count = this.baseMapper.deleteById(id);
        if (count > 0) {
            gridDataService.clearLegendCacheById(oldLegend.getType() == 1 ? oldLegend.getDataId() : oldLegend.getAlgorithmId());
            clearTileCache(oldLegend);
        }
        return count;
    }

    private void deleteTileCache(String path) {
        log.info("开始删除切片缓存：" + path);
        FileUtils.del(path);
        log.info("删除切片缓存结束：" + path);
    }

    public void clearTileCache(BusLegendInfo legendInfo) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO 删除配色时将对应的切片缓存清除掉，这里暂不清除
                if (legendInfo.getType() == 1) {
                    BusDataInfo dataInfo = Optional.ofNullable(dataInfoService.getById(legendInfo.getDataId())).orElseThrow(() -> new GlobalException("配色绑定的数据不存在"));
                    if (dataInfo.getType() == 1) {
                        //如果为栅格数据，则删除对应配色的切片缓存
                        deleteTileCache(tileProperties.getGrid().getTileDir() + File.separator + dataInfo.getId() + File.separator + legendInfo.getId());
                    }
                } else if (legendInfo.getType() == 2) {
                    //如果为算法，则删除对应的算法产生的切片
                    BusAlgorithmInfo algorithmInfo = Optional.ofNullable(algorithmInfoService.getById(legendInfo.getAlgorithmId())).orElseThrow(() -> new GlobalException("配色绑定的算法不存在"));
                    deleteTileCache(algorithmInfo.getTilePath() + File.separator + File.separator + legendInfo.getId());
                }
            }
        }).start();
    }

    @Override
    public int updateLegend(BusLegendInfo legendInfo) {
        checkLegend(legendInfo);
        int count = this.baseMapper.updateById(legendInfo);
        if (count > 0) {
            gridDataService.clearLegendCacheById(legendInfo.getType() == 1 ? legendInfo.getDataId() : legendInfo.getAlgorithmId());
            clearTileCache(legendInfo);
        }
        return count;
    }

    @Override
    public List<BusLegendInfo> getLegendList(int type, Long id) {
        List<BusLegendInfo> legendInfos = this.lambdaQuery().eq(BusLegendInfo::getType, type)
            .eq(type == 1, BusLegendInfo::getDataId, id)
            .eq(type == 2, BusLegendInfo::getAlgorithmId, id)
            .orderByDesc(BusLegendInfo::getCreateTime)
            .list();
        return legendInfos;
    }

    @Override
    public BusLegendInfo getEnabledLegend(int type, Long id) {
        BusLegendInfo legendInfo = this.lambdaQuery().eq(BusLegendInfo::getType, type)
            .eq(type == 1, BusLegendInfo::getDataId, id)
            .eq(type == 2, BusLegendInfo::getAlgorithmId, id)
            .eq(BusLegendInfo::getEnabled, true)
            .one();
        return legendInfo;
    }

    @Override
    public BusLegendInfo getEnabledLegend2(int type, Long id, String dataType) {
        BusLegendInfo legendInfo = this.lambdaQuery().eq(BusLegendInfo::getType, type)
            .eq(BusLegendInfo::getAlgorithmId, id)
            .eq(BusLegendInfo::getDataType, dataType)
            .one();
        return legendInfo;
    }


    @Override
    public void enabledLegend(Long id, Boolean enabled) {
        BusLegendInfo legendInfo = Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("配色不存在"));
        if (enabled) {
            //如果是启用，则先关闭其它启用的配色，每次只能启用一个配色
            this.lambdaUpdate()
                .eq(legendInfo.getType() == 1, BusLegendInfo::getDataId, legendInfo.getDataId())
                .eq(legendInfo.getType() == 2, BusLegendInfo::getAlgorithmId, legendInfo.getAlgorithmId())
                .eq(BusLegendInfo::getType, legendInfo.getType())
                .set(BusLegendInfo::getEnabled, false)
                .update();
        }
        boolean flag = this.lambdaUpdate()
            .eq(BusLegendInfo::getId, id)
            .set(BusLegendInfo::getEnabled, enabled)
            .update();
        if (flag)
            gridDataService.clearLegendCacheById(legendInfo.getType() == 1 ? legendInfo.getDataId() : legendInfo.getAlgorithmId());
    }

    @Override
    public List<Map<String, Object>> importSld(MultipartFile file) throws Exception {
        List<Map<String, Object>> resultList = new ArrayList<>();
        BusFileInfo fileInfo = fileInfoService.uploadFile(file, tileProperties.getSld().getUploadPath());
        //解析sld文件
        File sldFile = new File(fileInfo.getPath());
        if (sldFile.exists()) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            DefaultHandler handler = new DefaultHandler() {
                private String tagName;
                private String name;
                private String label;
                private String color;
                private String quantity;
                private Map<String, Object> resultMap;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    tagName = qName;
                    name = attributes.getValue("name");
                    label = attributes.getValue("label");
                    color = attributes.getValue("color");
                    quantity = attributes.getValue("quantity");
                    if (Strings.isNotBlank(tagName)) {

                        if (tagName.equals("se:Rule") || tagName.equals("sld:Rule")) {
                            resultMap = new HashMap<>();
                        }
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    String text = new String(ch, start, length);
                    if (Strings.isNotBlank(tagName)) {
                        if (Strings.isNotBlank(text)) {
                            if (tagName.equals("ogc:PropertyName")) {
                                resultMap.put("field", text);
                            }
                            if (tagName.equals("ogc:Literal")) {
                                resultMap.put("value", text);
                            }
                            if (tagName.equals("se:SvgParameter") && name.equals("fill")) {
                                resultMap.put("color", text);
                            }
                            if (tagName.equals("se:Title")) {
                                resultMap.put("label", text);
                            }
                            if (tagName.equals("sld:ColorMapEntry")) {
                                resultMap.put("value", quantity);
                                resultMap.put("color", color);
                                resultMap.put("label", label);
                                resultList.add(resultMap);
                            }
                        }
                        if (tagName.equals("sld:ColorMapEntry")) {
                            resultMap.put("value", quantity);
                            resultMap.put("color", color);
                            resultMap.put("label", label);
                            resultList.add(resultMap);
                            resultMap = new HashMap<>();
                            tagName = null;
                            name = null;
                            label = null;
                            quantity = null;
                            color = null;
                        }
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
//                    FileUtils.downloadFile();
                    if (qName.equals("se:Rule")) {
                        //针对分级类型，检查是否重复添加过
                        if (!resultList.stream().map(map -> map.get("value")).collect(Collectors.toList()).contains(resultMap.get("value"))) {
                            resultList.add(resultMap);
                        }
                        resultMap = null;
                        tagName = null;
                        name = null;
                    }
                }
            };
            saxParser.parse(sldFile, handler);
        }
        return resultList;
    }

    private void checkLegend(BusLegendInfo legendInfo) {
        if (Strings.isNotBlank(legendInfo.getDataType()) && legendInfo.getType() == 2) {
            boolean exist = this.lambdaQuery()
                .ne(Objects.nonNull(legendInfo.getId()), BusLegendInfo::getId, legendInfo.getId())
                .eq(BusLegendInfo::getDataType, legendInfo.getDataType())
                .eq(BusLegendInfo::getAlgorithmId, legendInfo.getAlgorithmId())
                .exists();
            if (exist) {
                throw new ServiceException("改算法的数据类型已有配色");
            }

        }
    }
}
