package com.htht.algorithm.data.tile.vector.service;

import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.tile.vector.entity.ShpInfo;
import com.htht.algorithm.data.tile.vector.utils.ShpUtil;
import com.htht.algorithm.data.tile.utils.CmdCallBack;
import com.htht.algorithm.data.tile.utils.CmdTask;
import com.htht.algorithm.data.tile.utils.ZipUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

/**
 * shp服务类
 */
@Slf4j
public abstract class VectorService<T> {

    public final TileProperties tileProperties;

    protected VectorService(TileProperties tileProperties) {
        this.tileProperties = tileProperties;
    }


    /**
     * TODO 根据ID拿到shp压缩包路径，由具体业务去实现
     *
     * @param id
     * @return
     */
    public abstract String getPath(T id);

    public ShpInfo getInfo(String shpPath) {
        return ShpUtil.getInfo(shpPath);
    }

    public void tile(String shpPath, String tileDir, int minZoom, int maxZoom, CmdCallBack callBack) {
        //将shp文件转为json文件
        File file = new File(shpPath);
        if (!file.exists()) {
            throw new GlobalException("shp文件不存在：" + shpPath);
        }
        String jsonPath = file.getParent() + File.separator + FileUtils.getPrefix(file) + ".json";
        jsonPath = jsonPath.replace("\\", "/");
        ShpUtil.shp2json(shpPath, jsonPath);
        String[] command = new String[]{
            tileProperties.getVector().getCygwinPath(),
            "-c",
            "tippecanoe -aw -e " + tileDir + " -pC -Z" + tileProperties.getVector().getMinZoom() + " -z" + tileProperties.getVector().getMaxZoom() + " -f " + jsonPath
        };
        log.info("开始执行：" + Arrays.toString(command));
        String[] env = new String[]{tileProperties.getVector().getTippecanoePath()};
        CmdTask task = new CmdTask(command, env, callBack);
        task.run();
    }

    /**
     * 解压shp文件到指定目录并返回.shp文件路径
     *
     * @param shpZipPath
     * @param shpUnzipPath
     * @return
     */
    public String unShpZip(String shpZipPath, String shpUnzipPath) {
        log.info("解压shp文件开始：{}", shpZipPath);
        String extension = FileUtils.getSuffix(shpZipPath);
        File file = new File(shpZipPath);
        if ("zip".equals(extension)) {
            ZipUtil.unZip(file, shpUnzipPath, false);
        } else if ("7z".equals(extension)) {
            ZipUtil.un7z(file, shpUnzipPath, false);
        } else if ("tar".equals(extension)) {
            ZipUtil.unTar(file, shpUnzipPath, false);
        }
        File unZipFile = new File(shpUnzipPath);
        File[] files = unZipFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".shp");
            }
        });
        if (files.length != 1) {
            throw new GlobalException("没有找到shp文件或找到多个shp文件");
        }
        log.info("解压shp文件结束：{}", shpZipPath);
        return files[0].getAbsolutePath();
    }
}
