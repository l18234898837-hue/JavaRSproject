package com.htht.algorithm.data.tile.grid.utils;

import cn.hutool.core.util.ArrayUtil;
import com.htht.algorithm.data.domain.vo.ColorRampVo;
import com.htht.algorithm.data.tile.grid.entity.GridInfo;
import com.htht.algorithm.data.tile.grid.entity.LegendInfo;
import com.htht.common.exception.UtilException;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.file.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.*;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * 栅格数据切片工具类
 *
 * @author wjx
 * @date 2023-4-19
 */
@Slf4j
public class GridUtil {

    private static final int WGS_84_EPSG_CODE = 4326;

    private static final String GEO_TIFF_DRIVER = "GTiff";
    private static final String PNG_DRIVER = "PNG";
    private static final String MEM_DRIVER = "MEM";
    public static final int NO_DATA = -9999;

    private final static String GDAL_FILENAME_IS_UTF8 = "YES";
    private final static String SHAPE_ENCODING = "CP936";
    public final static SpatialReference WGS84 = new SpatialReference();


    static {
//        System.out.println("java.library.path: " + System.getProperty("java.library.path"));
        System.loadLibrary("gdalalljni");
        gdal.AllRegister();
        //注册所有驱动
        ogr.RegisterAll();
        //为了支持中文路径，请添加下面这句代码
        gdal.SetConfigOption("GDAL_FILENAME_IS_UTF8", GDAL_FILENAME_IS_UTF8);
        //为了使属性表字段支持中文
        // linux 下中文需要 GB2312
        if (System.getProperties().getProperty("os.name").toUpperCase().contains("WINDOWS")) {
            gdal.SetConfigOption("SHAPE_ENCODING", SHAPE_ENCODING);
        } else {
            gdal.SetConfigOption("SHAPE_ENCODING", "GB2312");
        }
        //启用调试输出
//        gdal.SetConfigOption("CPL_DEBUG", "ON");
        WGS84.ImportFromEPSG(4326);
//        System.load("D:\\tools\\opencv\\build\\java\\x64\\opencv_java470.dll");
    }

    /**
     * 瓦片坐标转经度
     *
     * @param x
     * @param z
     * @return
     */
    public static double tile2lng(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    /**
     * 瓦片坐标转纬度
     *
     * @param y
     * @param z
     * @return
     */
    public static double tile2lat(int y, int z) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z))));
    }

    /**
     * 经纬度坐标转切片索引
     *
     * @param lng
     * @param lat
     * @param zoom
     * @return
     */
    public static int[] coordinates2tile(double lng, double lat, int zoom) {
        int x = (int) Math.floor((lng + 180) / 360 * (1 << zoom));
        int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (x < 0) {
            x = 0;
        }
        if (x >= (1 << zoom)) {
            x = ((1 << zoom) - 1);
        }
        if (y < 0) {
            y = 0;
        }
        if (y >= (1 << zoom)) {
            y = ((1 << zoom) - 1);
        }
        return new int[]{x, y};
    }

    /**
     * 经纬度坐标转投影坐标转换器
     *
     * @param proj
     * @return
     */
    private static CoordinateTransformation wgs842ProjTransformation(String proj) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromWkt(proj);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 投影坐标转经纬度坐标转化器
     *
     * @param epsg
     * @return
     */
    private static CoordinateTransformation proj2Wgs84Transformation(Integer epsg) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromEPSG(epsg);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 投影坐标转经纬度坐标转化器
     *
     * @param wkt
     * @return
     */
    private static CoordinateTransformation proj2Wgs84Transformation(String wkt) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromWkt(wkt);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 投影坐标转经纬度坐标转化器2
     *
     * @param wkt
     * @return
     */
    private static CoordinateTransformation proj2Wgs84TransformationByWkt(String wkt) {
        SpatialReference inSpatialReference = new SpatialReference();
        inSpatialReference.ImportFromWkt(wkt);

        SpatialReference outSpatialReference = new SpatialReference();
        outSpatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);
        return new CoordinateTransformation(inSpatialReference, outSpatialReference);
    }

    /**
     * 得到影像的基本信息
     *
     * @param path
     * @return
     */
    public static GridInfo getInfo(String path) {
        Dataset dataset = gdal.Open(path, gdalconstConstants.GA_ReadOnly);
        if (Objects.isNull(dataset)) {
            throw new UtilException("栅格数据不存在或打开失败:" + path);
        }
        String proj = dataset.GetProjection();
        if (StringUtils.isBlank(proj)) {
            throw new UtilException("栅格数据不带坐标参考信息:" + path);
        }

        //获取遥感影像经纬度范围，计算遥感影像分辨率
        double[] geoTransform = dataset.GetGeoTransform();
        int bandCount = dataset.getRasterCount();
        int dataType = dataset.GetRasterBand(1).getDataType();
        //获取影像的像素分辨率
        int xSize = dataset.GetRasterXSize();
        int ySize = dataset.GetRasterYSize();
        //计算经纬度范围
        double xMin = geoTransform[0];
        double yMax = geoTransform[3];
        double xMax = xMin + (xSize * geoTransform[1]) + (ySize * geoTransform[2]);
        double yMin = yMax + (xSize * geoTransform[4]) + (ySize * geoTransform[5]);
        double srcWePixelResolution = (xMax - xMin) / xSize;
        double srcNsPixelResolution = (yMax - yMin) / ySize;

        double lngMin = xMin, lngMax = xMax, latMin = yMin, latMax = yMax;
        //这里针对不是84的坐标系，需要转成经纬度
        if (!is4326projByWkt(proj)) {
            CoordinateTransformation proj2Wgs84Transformation = proj2Wgs84Transformation(proj);
            double[] lngLatMin = proj2Wgs84Transformation.TransformPoint(xMin, yMin);
            double[] lngLatMax = proj2Wgs84Transformation.TransformPoint(xMax, yMax);
            lngMin = lngLatMin[0];
            latMin = lngLatMin[1];
            lngMax = lngLatMax[0];
            latMax = lngLatMax[1];
        }

        //计算百分比截断值
        //获取最大最小值
        Band band = dataset.GetRasterBand(1);

        double[] minMax = new double[2];
        band.ComputeRasterMinMax(minMax, 1);
        double min = minMax[0];
        double max = minMax[1];
        dataset.delete();

        return new GridInfo(path, proj, geoTransform, bandCount, dataType,
            xSize, ySize, xMin, xMax, yMin, yMax, lngMin, lngMax, latMin, latMax,
            srcWePixelResolution, srcNsPixelResolution, min, max);
    }

    /**
     * 判断影像是否是4326坐标系
     *
     * @param input
     * @return
     */
    public static boolean is4326proj(String input) {
        Dataset ds = gdal.Open(input, gdalconstConstants.GA_ReadOnly);
        String proj = ds.GetProjection();
        ds.delete();
        return is4326projByWkt(proj);
    }

    public static boolean is4326projByWkt(String wkt) {
        SpatialReference srs = new SpatialReference();
        srs.ImportFromWkt(wkt);

        SpatialReference srsWGS84 = new SpatialReference();
        srsWGS84.ImportFromEPSG(4326);
        int isWGS84 = srs.IsSame(srsWGS84);

        return isWGS84 == 1;
    }


    /**
     * 生成缩略图
     *
     * @param input
     * @param output
     */
    public static void createThumb(String input, String output,ColorRampVo colorRampVo) {
        log.info("--------开始生成缩略图:{}->{}--------------", input, output);
        Dataset ds = gdal.Open(input);
        if (ds == null) {
            log.error("无法打开图像文件: {}", input);
            return; // 打开失败时退出
        }

        try {
            // 获取图像的波段数量
            int bandCount = ds.getRasterCount();
            log.info("图像波段数: {}", bandCount);

            // 根据波段数添加-b参数（单波段只取第1波段，多波段取前3波段）
            if (bandCount == 1) {
                generateHeatmapFromTif(
                    input,
                    output,
                    colorRampVo
                );
            }else {
                // 构建gdal.Translate的命令参数（动态调整波段参数）
                List<String> cmdList = new ArrayList<>();
                cmdList.add("-of");
                cmdList.add("JPEG"); // 输出格式
                cmdList.add("-ot");
                cmdList.add("Byte"); // 输出数据类型
                cmdList.add("-scale"); // 线性拉伸
                // 多波段：使用4、3、2波段（需确保影像至少有4个波段）
                // 先判断波段数是否足够，避免无效参数
                if (bandCount >= 4) {
                    cmdList.add("-b");
                    cmdList.add("4"); // 红色通道对应第4波段
                    cmdList.add("-b");
                    cmdList.add("3"); // 绿色通道对应第3波段
                    cmdList.add("-b");
                    cmdList.add("2"); // 蓝色通道对应第2波段
                } else {
                    // 波段数不足4时的降级处理（例如仍用前3波段，或报错）
                    log.warn("影像波段数不足4（实际{}波段），将使用前3波段生成缩略图", bandCount);
                    int bandsToTake = Math.min(bandCount, 3);
                    for (int i = 1; i <= bandsToTake; i++) {
                        cmdList.add("-b");
                        cmdList.add(String.valueOf(i));
                    }
                }
                // 将参数列表转换为数组并解析
                String[] cmdArray = cmdList.toArray(new String[0]);
                Vector vector = gdal.ParseCommandLine(String.join(" ", cmdArray));
                TranslateOptions opt = new TranslateOptions(vector);

                // 执行缩略图生成
                gdal.Translate(output, ds, opt);
            }
        } finally {
            // 释放资源
            if (ds != null) {
                ds.delete();
            }
            log.info("--------结束生成缩略图:{}->{}--------------", input, output);
        }
    }
    /**
     * 影像压缩：高位深转8位
     * 百分比截断方式进行压缩
     *
     * @param input
     * @param out
     */
    public static void compress(String input, String out) {
        log.info("影像压缩开始：{}-->{}", input, out);
        GridInfo info = getInfo(input);
        Dataset dataset = gdal.Open(info.getPath(), gdalconstConstants.GA_ReadOnly);
        Driver driver = gdal.GetDriverByName(GEO_TIFF_DRIVER);
        Dataset outDs = driver.Create(out, info.getXSize(), info.getYSize(), info.getBandCount(), gdalconstConstants.GDT_Byte);
        outDs.SetGeoTransform(info.getGeoTransform());
        outDs.SetProjection(info.getProj());

        for (int i = 0; i < info.getBandCount(); i++) {
            Band inBand = dataset.GetRasterBand(i + 1);
            Band outBand = outDs.GetRasterBand(i + 1);

            double[] minMax = new double[2];
            inBand.ComputeRasterMinMax(minMax, 0);
            int bandMin = (int) minMax[0];
            int bandMax = (int) minMax[1];
            int[] panHistogram = new int[bandMax - bandMin + 1];

            inBand.GetHistogram(bandMin, bandMax, panHistogram, true, false);

            int total = Arrays.stream(panHistogram).sum();
            int[] cut = cumulativeCut(panHistogram, total, 2, 2, bandMin);
            double compressScale = (double) (cut[1] - cut[0]) / 255.0;
            for (int j = 0; j < info.getYSize(); j++) {
                log.info("开始第" + (j + 1) + "行数据压缩");
                int[] xData = new int[info.getXSize()];
                inBand.ReadRaster(0, j, info.getXSize(), 1, xData);
                double[] compressData = new double[info.getXSize()];
                for (int k = 0; k < info.getXSize(); k++) {
                    int tempVal = xData[k];
                    if (xData[k] < cut[0]) {
                        xData[k] = cut[0];
                    }
                    if (xData[k] > cut[1]) {
                        xData[k] = cut[1];
                    }
                    compressData[k] = (xData[k] - cut[0]) / compressScale;
                    if (compressData[k] == 0 && tempVal > 0) {
                        compressData[k] = 1;
                    }
                }
                outBand.WriteRaster(0, j, info.getXSize(), 1, compressData);
            }
            log.info("结束压缩" + (i + 1) + "个波段");
        }
        outDs.FlushCache();
        outDs.delete();
        dataset.delete();
        log.info("影像压缩结束：{}-->{}", input, out);
    }

    /**
     * 根据截断百分比计算上下的截断值
     *
     * @param histogram
     * @param counts
     * @param minPercent
     * @param maxPercent
     * @param bandMin
     * @return
     */
    private static int[] cumulativeCut(int[] histogram, int counts, int minPercent, int maxPercent, int bandMin) {
        double minCountPercent = counts * (minPercent / 100.0);
        double maxCountPercent = counts * (1 - maxPercent / 100.0);

        int cutMin = 0, cutMax = 0;
        for (int i = 1; i < histogram.length; i++) {
            histogram[i] += histogram[i - 1];
            if (histogram[i] >= minCountPercent && histogram[i - 1] <= minCountPercent) {
                cutMin = i + bandMin;
            }
            if (histogram[i] >= maxCountPercent && histogram[i - 1] <= maxCountPercent) {
                cutMax = i + bandMin;
            }
        }
        return new int[]{cutMin, cutMax};
    }

    public static boolean buildOverviews(String path) {
        return buildOverviews(path, "nearest");
    }

    /**
     * 创建影像金字塔
     *
     * @param path
     */
    public static boolean buildOverviews(String path, String resampling) {
        log.info("建立金字塔开始：{}", path);
        Dataset dataset = gdal.Open(path, gdalconstConstants.GA_ReadOnly);
        dataset.BuildOverviews(resampling, new int[]{2, 4, 8, 16});
        dataset.delete();
        log.info("建立金字塔结束：{}", path);
        return true;
    }

    /**
     * 栅格投影转换
     *
     * @param input
     * @param out
     * @param code
     */
    public static void projTransform(String input, String out, int code) {
        log.info("投影转换{}开始：{}-->{}", code, input, out);
        GridInfo info = getInfo(input);
        Dataset dataset = gdal.Open(input, gdalconstConstants.GA_ReadOnly);
        SpatialReference spatialReference = new SpatialReference();
        spatialReference.ImportFromEPSG(code);
        Dataset autoDs = gdal.AutoCreateWarpedVRT(dataset, null, spatialReference.ExportToWkt(), gdalconstConstants.GRA_Bilinear);
        Dataset outDs = gdal.GetDriverByName(GEO_TIFF_DRIVER).CreateCopy(out, autoDs);
        outDs.FlushCache();
        outDs.delete();
        dataset.delete();
        autoDs.delete();
        log.info("投影转换{}结束：{}-->{}", code, input, out);
    }

    public static double[][] getBandData(Dataset dataset, int tileSize, int offsetX, int offsetY, int blockX, int blockY, int imageXBuf, int imageYBuf) {
        int bandCount = dataset.GetRasterCount();
        double[][] bandsData = new double[bandCount][tileSize * tileSize];
        for (int i = 1; i <= bandCount; i++) {
            Band band = dataset.GetRasterBand(i);
            double[] data = new double[tileSize * tileSize];
            band.ReadRaster(offsetX, offsetY, blockX, blockY, imageXBuf, imageYBuf, gdalconstConstants.GDT_Float64, data);
            bandsData[i] = data;
        }
        return bandsData;
    }

    /**
     * 设置坐标系和坐标
     *
     * @param path
     * @param lngMin
     * @param lngMax
     * @param latMin
     * @param latMax
     */
    public static void setProjAndTransform(String path, double lngMin, double lngMax, double latMin, double latMax) {
        Dataset ds = gdal.Open(path, gdalconstConstants.GA_ReadOnly);
        SpatialReference spatialReference = new SpatialReference();
        spatialReference.ImportFromEPSG(WGS_84_EPSG_CODE);
        ds.SetProjection(spatialReference.ExportToWkt());

        int xSize = ds.GetRasterXSize();
        int ySize = ds.GetRasterYSize();
        double xPixel = (lngMax - lngMin) / xSize;
        double yPixel = (latMin - latMax) / ySize;
        double[] transform = new double[]{lngMin, xPixel, 0.0, latMax, 0.0, yPixel};
        ds.SetGeoTransform(transform);
        ds.FlushCache();
        ds.delete();
    }

    public static double[] transformCoordinate(double[] coordinate, String wkt) {
        CoordinateTransformation transformation = proj2Wgs84TransformationByWkt(wkt);
        return transformation.TransformPoint(coordinate[0], coordinate[1]);
    }

    public static double[][] transformCoordinates(double[][] coordinates, int epsg) {
        CoordinateTransformation transformation = proj2Wgs84Transformation(epsg);
        double[][] resultArray = new double[coordinates.length][2];
        for (int i = 0; i < coordinates.length; i++) {
            double[] point = transformation.TransformPoint(coordinates[i][0], coordinates[i][1]);
            resultArray[i] = point;
        }
        return resultArray;
    }

    private final static ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    /**
     * tif全波段切片
     *
     * @param info
     * @param tifTilePath
     * @param tileSize
     * @param zoom
     * @param row
     * @param col
     */
    public static void tifTile(GridInfo info, String tifTilePath, int tileSize, int zoom, int row, int col) {
        Semaphore semaphore = semaphores.computeIfAbsent(tifTilePath, k -> new Semaphore(1));
        try {
            semaphore.acquire();
            if (!FileUtils.exist(tifTilePath)) {
                Dataset dataset = gdal.Open(info.getPath(), gdalconstConstants.GA_ReadOnly);
                double tempLatMin = tile2lat(row + 1, zoom);
                double tempLatMax = tile2lat(row, zoom);
                double tempLonMin = tile2lng(col, zoom);
                double tempLonMax = tile2lng(col + 1, zoom);

                //当前切片的像素分辨率
                double tileXPixelResolution = (tempLonMax - tempLonMin) / tileSize;
                double tileYPixelResolution = (tempLatMax - tempLatMin) / tileSize;

                double tileRealXMin = (tempLonMin < info.getLngMin() ? info.getLngMin() : tempLonMin);
                double tileRealXMax = (tempLonMax > info.getLngMax() ? info.getLngMax() : tempLonMax);
                double tileRealYMin = (tempLatMin < info.getLatMin() ? info.getLatMin() : tempLatMin);
                double tileRealYMax = (tempLatMax > info.getLatMax() ? info.getLatMax() : tempLatMax);
                int offsetX = (int) Math.ceil((tileRealXMin - info.getLngMin()) / info.getSrcWePixelResolution());
                int offsetY = (int) Math.ceil((info.getLatMax() - tileRealYMax) / info.getSrcNsPixelResolution());
                int blockX = (int) Math.floor((tileRealXMax - tileRealXMin) / info.getSrcWePixelResolution());
                int blockY = (int) Math.floor((tileRealYMax - tileRealYMin) / info.getSrcNsPixelResolution());

                int imageXBuf = (int) Math.ceil((tileRealXMax - tileRealXMin) / tileXPixelResolution);
                int imageYBuf = (int) Math.ceil((tileRealYMax - tileRealYMin) / tileYPixelResolution);
                imageXBuf = Math.min(imageXBuf, tileSize);
                imageYBuf = Math.min(imageYBuf, tileSize);

                int imageOffsetX = (int) ((tileRealXMin - tempLonMin) / tileXPixelResolution);
                int imageOffsetY = (int) ((tempLatMax - tileRealYMax) / tileYPixelResolution);
                imageOffsetX = Math.max(imageOffsetX, 0);
                imageOffsetY = Math.max(imageOffsetY, 0);

                int bandCount = dataset.GetRasterCount();
                Band band = dataset.GetRasterBand(1);
                int dataType = band.GetRasterDataType();
                Driver memDriver = gdal.GetDriverByName(MEM_DRIVER);
                Dataset memDs = memDriver.Create("memDS", tileSize, tileSize, bandCount, dataType);
                for (int i = 1; i <= bandCount; i++) {
                    Band inputBand = dataset.GetRasterBand(i);
                    double[] data = new double[tileSize * tileSize];
                    inputBand.ReadRaster(offsetX, offsetY, blockX, blockY, imageXBuf, imageYBuf, gdalconstConstants.GDT_Float64, data);
                    Band outBand = memDs.GetRasterBand(i);
                    outBand.WriteRaster(imageOffsetX, imageOffsetY, imageXBuf, imageYBuf, gdalconstConstants.GDT_Float64, data);
                }

                Driver imageDriver = gdal.GetDriverByName(GEO_TIFF_DRIVER);
                File tileFile = new File(tifTilePath);
                tileFile.getParentFile().mkdirs();
                Dataset outDs = imageDriver.CreateCopy(tifTilePath, memDs);
                outDs.FlushCache();
                outDs.delete();
                memDs.FlushCache();
                memDs.delete();
                dataset.delete();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    /**
     * tif转png,根据波段组合
     *
     * @param tifPath
     * @param pngPath
     */
    private static void tif2PngByBandsIdx(String tifPath, String pngPath, int[] bandsIdx, double min, double max) {
        FileUtils.mkParentDirs(pngPath);
        Dataset dataset = gdal.Open(tifPath, gdalconstConstants.GA_ReadOnly);
        StringBuilder command = new StringBuilder();
        command.append("-of PNG -ot Byte -a_nodata 0 -scale");
        command.append(" ").append(min).append(" ").append(max);
        command.append(" 0 255");
        for (int idx : bandsIdx) {
            command.append(" -b ").append(idx);
        }
        Vector vector = gdal.ParseCommandLine(command.toString());
        TranslateOptions opt = new TranslateOptions(vector);
        gdal.Translate(pngPath, dataset, opt);
        dataset.delete();
    }

    private static void tif2PngByColorMap(String tifPath, String pngPath, LegendInfo legendInfo) throws IOException {
        Dataset dataset = gdal.Open(tifPath, gdalconstConstants.GA_ReadOnly);
        Band inputBand = dataset.GetRasterBand(1);
        int xSize = dataset.GetRasterXSize();
        int ySize = dataset.GetRasterYSize();

        //读取数据，按图例重新赋值
        double[] bandData = new double[xSize * ySize];
        inputBand.ReadRaster(0, 0, xSize, ySize, gdalconstConstants.GDT_Float64, bandData);

        List<LegendInfo.Grade> gradeList = legendInfo.getLegendList();
        int type = legendInfo.getType();

        if (type == 3) {
            for (int i = 0; i < bandData.length; i++) {
                double value = bandData[i];
                for (int j = 0; j < gradeList.size(); j++) {
                    LegendInfo.Grade grade = gradeList.get(j);
                    double gradeValue = Double.parseDouble(grade.getValue());

                    if (j < gradeList.size() - 1 && value < gradeValue) {
                        bandData[i] = gradeValue;
                        break;
                    }

                    if (j == gradeList.size() - 1 && value >= gradeValue) {
                        bandData[i] = gradeValue;
                        break;
                    }
                }
            }
        }
        double[][] newBandArray = GridUtil.resetBandsDataByValueArray(Arrays.stream(bandData).boxed().collect(Collectors.toList()), xSize, ySize, legendInfo, 0);
        GridUtil.rgbArray2Image(newBandArray, xSize, ySize, pngPath);
    }

    public static double[][] resetBandsDataByValueArray(List<Double> bandData, int xSize, int ySize, LegendInfo legendInfo, double noDataValue) {
        double[][] newBandsData = new double[3][xSize * ySize];
        for (int i = 0; i < bandData.size(); i++) {
            double value = bandData.get(i);
            LegendInfo.Grade grade = getGradeByValue(value, legendInfo.getLegendList(), legendInfo.getType(), noDataValue);
            for (int j = 0; j < 3; j++) {
                if (Objects.isNull(grade)) {
                    newBandsData[j][i] = 0;
                } else {
                    //color格式为rgb(243,23,2),需要提取其中R\G\B的值
                    String color = grade.getColor();
                    int[] rgb = getRGBByColor(color);
                    newBandsData[j][i] = rgb[j];
                }
            }
        }
        return newBandsData;
    }

    public static LegendInfo.Grade getGradeByValue(double value, List<LegendInfo.Grade> gradeList, int type, double noDataValue) {
        for (int i = 0; i < gradeList.size(); i++) {
            double gradeValue = Double.valueOf(gradeList.get(i).getValue());
            if (type == 2) {
                if (value == gradeValue) {
                    return gradeList.get(i);
                }
            } else if (type == 3) {
                if (value < gradeValue) {
                    return gradeList.get(i);
                }
            }
        }
        return null;
    }

    public static int[] getRGBByColor(String color) {
        int[] rgb = new int[3];
        if (color.startsWith("#")) {
            rgb[0] = Integer.parseInt(color.substring(1, 3), 16);
            rgb[1] = Integer.parseInt(color.substring(3, 5), 16);
            rgb[2] = Integer.parseInt(color.substring(5, 7), 16);
        } else {
            String[] rgbStr = color.split("\\(")[1].split("\\)")[0].split(",");

            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = Integer.parseInt(rgbStr[i].trim());
            }
        }

        return rgb;
    }

    public static void rgbArray2Image(double[][] values, int xSize, int ySize, String path) throws IOException {
        BufferedImage bi = new BufferedImage(xSize, ySize, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < ySize; i++) {
            for (int j = 0; j < xSize; j++) {
                int idx = i * xSize + j;
                bi.setRGB(j, i, new Color((int) values[0][idx], (int) values[1][idx], (int) values[2][idx], ArrayUtil.max(values[0][idx], values[1][idx], values[2][idx]) > 0 ? 255 : 0).getRGB());
            }
        }
        File file = new File(path);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        ImageIO.write(bi, "png", file);
    }

    /**
     * tif转png，单波段根据配色表配色
     *
     * @param tifPath
     * @param pngPath
     * @param legendInfo
     */
    public static void tif2Png(String tifPath, String pngPath, LegendInfo legendInfo, double min, double max) throws IOException {
        int legendType = legendInfo.getType();
        if (legendType != 1) {
            tif2PngByColorMap(tifPath, pngPath, legendInfo);
        } else {
            tif2PngByBandsIdx(tifPath, pngPath, legendInfo.getBands(), min, max);
        }
    }

    /**
     * 从TIF文件生成无标签/色条的热力图PNG（用于地图叠加）
     * @param tifPath 输入TIF文件路径（需包含单波段数据）
     * @param outputPngPath 输出PNG文件路径
     */
    public static void generateHeatmapFromTif(String tifPath, String outputPngPath,ColorRampVo colorRampVo) {
        // 1. 读取TIF文件信息
        GridInfo gridInfo = getInfo(tifPath);
        Dataset dataset = gdal.Open(tifPath, gdalconstConstants.GA_ReadOnly);
        if (Objects.isNull(dataset)) {
            throw new UtilException("无法打开TIF文件: " + tifPath);
        }

        try {
            // 2. 获取单波段数据（热力图通常基于单波段）
            Band band = dataset.GetRasterBand(1);
            int xSize = gridInfo.getXSize();
            int ySize = gridInfo.getYSize();
            double[] flatData = new double[xSize * ySize];
            band.ReadRaster(0, 0, xSize, ySize, xSize, ySize, gdalconstConstants.GDT_Float64, flatData);

            // 转换为二维数组
            double[][] data = new double[ySize][xSize];
            for (int i = 0; i < ySize; i++) {
                System.arraycopy(flatData, i * xSize, data[i], 0, xSize);
            }

            // 3. 确定数据范围（使用自定义值或自动计算）
            double actualVmin = (colorRampVo.getMinValue() != null) ? colorRampVo.getMinValue() : gridInfo.getMinValue();
            double actualVmax = (colorRampVo.getMaxValue() != null) ? colorRampVo.getMaxValue() : gridInfo.getMaxValue();
            if (actualVmin >= actualVmax) {
                throw new UtilException("vmin必须小于vmax");
            }

            // 4. 数据归一化
            double[][] normalizedData = new double[ySize][xSize];
            for (int i = 0; i < ySize; i++) {
                for (int j = 0; j < xSize; j++) {
                    double val = data[i][j];
                    // 处理无数据值
                    if (val == NO_DATA|| Double.isNaN(val)||val==0) {
                        normalizedData[i][j] = -1; // 标记为透明
                        continue;
                    }
                    // 截断并归一化
                    val = Math.max(actualVmin, Math.min(actualVmax, val));
                    normalizedData[i][j] = (val - actualVmin) / (actualVmax - actualVmin);
                }
            }

//            // 5. 定义jet_r配色方案（与之前保持一致）
//            List<int[]> jetRColors = Arrays.asList(
//                new int[]{128, 0, 0},     // 1.0 → 深红
//                new int[]{255, 0, 0},     // 0.8 → 红
//                new int[]{255, 255, 0},   // 0.6 → 黄
//                new int[]{0, 255, 255},   // 0.4 → 青
//                new int[]{0, 0, 255},     // 0.2 → 蓝
//                new int[]{0, 0, 143}     // 0.0 → 深蓝
//            );

            // 6. 生成图像（支持透明无数据区域）
            BufferedImage image = new BufferedImage(xSize, ySize, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < ySize; i++) {
                for (int j = 0; j < xSize; j++) {
                    double normVal = normalizedData[i][j];
                    if (normVal == -1) {
                        // ARGB：R=0, G=0, B=0, Alpha=0（完全透明）
                        image.setRGB(j, i, new Color(0, 0, 0, 0).getRGB());
                    } else {
                        // 有效数据：正常映射颜色
                        int[] rgb = getColorFromJetR(normVal, colorRampVo.getColorRamp());
                        image.setRGB(j, i, new Color(rgb[0], rgb[1], rgb[2]).getRGB());
                    }
                }
            }

            // 7. 保存为PNG
            File outputFile = new File(outputPngPath);
            FileUtils.mkdir(outputFile.getParentFile().getPath()); // 确保目录存在
            ImageIO.write(image, "PNG", outputFile);
            log.info("热力图PNG已生成: {}", outputPngPath);

        } catch (IOException e) {
            throw new UtilException("保存PNG失败: " + e.getMessage());
        } finally {
            if (dataset != null) {
                dataset.delete(); // 释放资源
            }
        }
    }

    /**
     * 复用jet_r颜色映射（保持原逻辑）
     */
    private static int[] getColorFromJetR(double normVal, List<int[]> jetRColors) {
        if (normVal <= 0) return jetRColors.get(0);
        if (normVal >= 1) return jetRColors.get(jetRColors.size() - 1);

        double step = 1.0 / (jetRColors.size() - 1);
        int idx = (int) Math.floor(normVal / step);
        double t = (normVal % step) / step;

        int[] color1 = jetRColors.get(idx);
        int[] color2 = jetRColors.get(idx + 1);
        return new int[]{
            (int) (color1[0] + t * (color2[0] - color1[0])),
            (int) (color1[1] + t * (color2[1] - color1[1])),
            (int) (color1[2] + t * (color2[2] - color1[2]))
        };
    }



    public static void main(String[] args) {
        gdal.AllRegister();
//        String path = "G:\\server\\GF1_PMS1_E111.6_N23.6_20161228_L1A0002094273-PAN1_ortho_fuse1.tif";
//
//        Dataset dataset = gdal.Open(path, gdalconstConstants.GA_ReadOnly);
//        Band band = dataset.GetRasterBand(1);
//        double[] minMax = new double[2];
//        band.ComputeRasterMinMax(minMax);
//        int xSize = dataset.GetRasterXSize();
//        int ySize = dataset.GetRasterYSize();
//        double min = minMax[0];
//        double max = minMax[1];
//        int nBuckets = (int) (max - min + 1);
//        int[] histogram = new int[nBuckets];
//        int result = band.GetHistogram(min, max, histogram, true, false, null);
//        if (result == gdalconstConstants.CE_None) {
//            System.out.println(Arrays.stream(histogram).sum() + "----" + xSize * ySize);
//        }
//
//        int dataType = band.GetRasterDataType();
//        double[] data = new double[xSize * ySize];
//        band.ReadRaster(5000, 5000, xSize, ySize, gdalconstConstants.GDT_Float64, data);
        generateHeatmapFromTif(
            "C:\\Users\\思大锤\\Desktop\\temp\\work\\L8_OLI_2014_04_24_02_44_00_120055_L2R_GuangxingJiao_L2A2_UMOPE_L3A2.tif",
            "C:\\Users\\思大锤\\Desktop\\temp\\work\\L8_OLI_2014_04_24_02_44_00_120055_L2R_GuangxingJiao_L2A2_UMOPE_L3A2.png",
            null
        );
//        System.out.println(data.length);
    }

}
