package com.htht.algorithm.data.tile.grid.service;

import cn.hutool.core.util.ArrayUtil;
import com.htht.algorithm.data.tile.common.MapListener;
import com.htht.algorithm.data.tile.common.MaxSizeHashMap;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.tile.grid.entity.GridInfo;
import com.htht.algorithm.data.tile.grid.entity.LegendInfo;
import com.htht.algorithm.data.tile.grid.utils.GridUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 栅格切片服务类
 *
 * @author wjx
 * @date 2023-4-21
 */
public abstract class GridService<T> {

    public final TileProperties tileProperties;

    /**
     * 影像信息的缓存
     */
    private final Map<T, GridInfo> gridMap;

    /**
     * 配色方案的缓存
     */
    private final Map<T, LegendInfo> legendInfoMap = new ConcurrentHashMap<>();
    private final Map<String, LegendInfo> legendInfoMap2 = new ConcurrentHashMap<>();

    public GridService(TileProperties tileProperties) {
        this.tileProperties = tileProperties;
        this.gridMap = new MaxSizeHashMap<>(this.tileProperties.getGrid().getMaxSize(), new MapListener() {
            @Override
            public void doAfterRemove(Object o) {
            }
        });
    }

    /**
     * TODO 根据id拿到影像路径，由具体业务去实现
     *
     * @param id
     * @return
     */
    public abstract String getPath(T id);

    /**
     * 根据id拿到配色方案，由具体业务去实现
     *
     * @param id
     * @return
     */
    public abstract LegendInfo getLegendInfo(T id, int type);

    public abstract LegendInfo getLegendInfo2(T id, String dataType, int type);

    private double[] intArr2DArr(int[] array) {
        double[] dArray = new double[array.length];
        for (int i = 0; i < dArray.length; i++) {
            dArray[i] = (double) array[i];
        }
        return dArray;
    }


    /**
     * 检查配色方案是否正确
     *
     * @param legendInfo
     * @param gridInfo
     * @return
     */
    private void checkLegend(LegendInfo legendInfo, GridInfo gridInfo) {
        if (Objects.isNull(legendInfo)) {
            throw new GlobalException("未找到相应的配色方案");
        }
        if (legendInfo.getType() == 1) {
            //如果是波段组合，需要满足波段数组最大索引不能大于波段数且数组长度为3
            if (legendInfo.getBands().length != 3) {
                throw new GlobalException("波段组合长度必须为3");
            }
            if (ArrayUtil.max(legendInfo.getBands()) > gridInfo.getBandCount()) {
                throw new GlobalException("波段组合中的最大索引大于波段数");
            }
            if (ArrayUtil.min(legendInfo.getBands()) <= 0) {
                throw new GlobalException("波段组合中的最小索引不能小于或等于0");
            }
        }
    }


    /**
     * 缓存影像信息
     *
     * @param id
     * @return
     */
    public GridInfo cacheInfo(T id) {
        GridInfo gridInfo = gridMap.get(id);
        if (Objects.isNull(gridInfo)) {
            String path = getPath(id);
            GridInfo info = GridUtil.getInfo(path);
            gridMap.put(id, info);
        }
        return gridInfo;
    }

    public LegendInfo cacheLegend(T id, int type) {
        if (legendInfoMap.containsKey(id)) {
            return legendInfoMap.get(id);
        } else {
            LegendInfo legendInfo = getLegendInfo(id, type);
            //检查配色是否正确
//            checkLegend(legendInfo, info);
            legendInfoMap.put(id, legendInfo);
            return legendInfo;
        }
    }

    public LegendInfo cacheLegend2(T id, int type, String dataType) {
        String key = id + "-" + dataType;
        if (legendInfoMap2.containsKey(key)) {
            return legendInfoMap2.get(key);
        } else {
            LegendInfo legendInfo = getLegendInfo2(id, dataType, type);
            //检查配色是否正确
//            checkLegend(legendInfo, info);
            legendInfoMap2.put(key, legendInfo);
            return legendInfo;
        }
    }

    /**
     * 提供一个对外的清除配色缓存的方法，当数据配色方案修改时清理下缓存
     *
     * @param id
     */
    public void clearLegendCacheById(T id) {
        if (legendInfoMap.containsKey(id)) {
            legendInfoMap.remove(id);
        }
    }

    public byte[] int2Bytes(int val) {
        byte[] result = new byte[4];
        result[0] = (byte) ((val >> 24) & 0xFF);
        result[1] = (byte) ((val >> 16) & 0xFF);
        result[2] = (byte) ((val >> 8) & 0xFF);
        result[3] = (byte) ((val & 0xFF));
        return result;
    }

    /**
     * 根据图层ID清理切片缓存
     * 这里分两种情况，1-原始数据，2-算法+数据N个,id格式为：算法ID-数据ID1_数据ID2_...数据IDn
     *
     * @param id
     */
    public void clearCache(String id) {
        String tileDir = tileProperties.getGrid().getTileDir();
        String[] arr = id.split("-");
        if (arr.length == 1) {
            //原始切片
            FileUtils.del(tileDir + File.separator + id);
        } else if (arr.length == 2) {
            //算法切片
            int algorithmId = Integer.parseInt(arr[0]);
            FileUtils.del(tileDir + File.separator + algorithmId);
        }
    }

}
