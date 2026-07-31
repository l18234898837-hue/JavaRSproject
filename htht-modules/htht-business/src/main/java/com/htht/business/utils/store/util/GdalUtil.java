package com.htht.business.utils.store.util;

import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.common.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.ogr.ogr;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/28 17:53
 **/
@Slf4j
public class GdalUtil {

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
        WGS84.ImportFromEPSG(4326);
    }

    /**
     * 使用 GDAL Java API 模拟 gdal_translate 命令
     * 功能：复制 TIFF 文件，并设置输出选项（Tiled, LZW压缩），自动读取 .rpb/.xml 中的地理信息
     *
     * @param srcFile  输入文件路径
     * @param dstFile  输出文件路径
     * @return 是否成功
     */
    public static boolean translateToGeoTiff(String srcFile, String dstFile) {
        Dataset srcDataset = null;
        Dataset dstDataset = null;

        try {
            // 设置配置选项（等价于 --config）
            gdal.SetConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "FALSE");
            gdal.SetConfigOption("CPL_LOG_ERRORS", "FALSE");
            gdal.SetConfigOption("GDAL_PAM_ENABLED", "NO");

            // 1. 打开输入数据集
            srcDataset = gdal.Open(srcFile, gdalconstConstants.GF_Read);
            if (srcDataset == null) {
                log.error("无法打开输入文件: " + srcFile);
                return false;
            }

            // 2. 获取 GeoTIFF 驱动
            Driver driver = gdal.GetDriverByName("GTiff");
            if (driver == null) {
                log.error("无法获取 GTiff 驱动");
                return false;
            }

            // 3. 设置创建选项（等价于 -co TILED=YES -co COMPRESS=LZW）
            String[] createOptions = {
                "TILED=YES",
                "COMPRESS=LZW",
                "BIGTIFF=IF_SAFER"  // 推荐：支持大文件
            };

            // 4. 创建输出文件（复制输入数据）
            dstDataset = driver.CreateCopy(dstFile, srcDataset, 0, createOptions);

            if (dstDataset == null) {
                log.error("创建输出文件失败: " + dstFile);
                return false;
            }

            // 可选：打印一些信息
            log.info("文件已成功转换: {} -> {}", srcFile, dstFile);
            log.info("投影: {}", dstDataset.GetProjection());
            double[] transform = dstDataset.GetGeoTransform();
            if (transform != null) {
                log.info("GeoTransform: [{}, {}, {}, {}, {}, {}]",
                    transform[0], transform[1], transform[2], transform[3], transform[4], transform[5]);
            }

            return true;

        } catch (Exception e) {
            log.error("gdal_translate 执行失败", e);
            return false;
        } finally {
            // 注意：GDAL Dataset 需要显式 delete() 释放资源
            if (dstDataset != null) {
                dstDataset.delete();
                dstDataset = null;
            }
            if (srcDataset != null) {
                srcDataset.delete();
                srcDataset = null;
            }
        }
    }

    public static boolean warpToUTM(String srcFile, String dstFile) {
        Dataset dstDataset = null;
        try {
            gdal.SetConfigOption("GDAL_CACHEMAX", "512"); // 512MB 缓存

            String[] optionsArray = {
                "-t_srs", "EPSG:32649",
                "-r", "bilinear",
                "-co", "TILED=YES",
                "-co", "COMPRESS=LZW",
                "-co", "BIGTIFF=IF_SAFER"
            };
            Vector<String> options = new Vector<>(Arrays.asList(optionsArray));
            dstDataset = gdal.Warp(dstFile, new Dataset[]{gdal.Open(srcFile)}, new WarpOptions(options));
            return dstDataset != null;
        } catch (Exception e) {
            log.error("Warp 失败", e);
            return false;
        } finally {
            if (dstDataset != null) dstDataset.delete();
        }
    }



    //GaoFen2数据把地理信息填入更改的tif中，然后获得其中内容
    public static String GaoFenToGeoTiff(File file){
        // 分离文件名和扩展名
        String directory = FileStrUtil.getDirectory(file);
        String extension = FileStrUtil.getExtension(file);
        String nameWithoutExtension = FileStrUtil.getNameWithoutExtension(file);
        String tran = directory + File.separator + nameWithoutExtension + "-tran" + extension;
        String outUrl = directory + File.separator + nameWithoutExtension + "-out" + extension;

        boolean isTran = translateToGeoTiff(file.getAbsolutePath(), tran);
        if (isTran) {
            boolean isWarp = warpToUTM(tran , outUrl );
            if (isWarp) {
                return outUrl;
            }else{
                throw new GlobalException("转化tran失败，检查原因");
            }
        }else{
            throw new GlobalException("转化warp失败，检查原因");
        }
    }

    //jp2转化成GeoTiff数据
    public static String Jp2ToGeoTiff (File file){
        String directory = FileStrUtil.getDirectory(file);
        String nameWithoutExtension = FileStrUtil.getNameWithoutExtension(file);
        String outUrl = directory + File.separator + nameWithoutExtension +".tif";
        Dataset jp2 = gdal.Open(file.getAbsolutePath(), gdalconstConstants.GF_Read);
        Driver tiffDriver=gdal.GetDriverByName(GEO_TIFF_DRIVER);
        String[] options ={"COMPRESS=DEFLATE"};
        Dataset out=tiffDriver.CreateCopy(outUrl,jp2,0,options);
        out.delete();
        jp2.delete();
        return outUrl;
    }

    public static void main(String[] args) {
        File file = new File("D:\\file\\浅海地形遥感反演平台\\5、参考数据\\数据入库_测试数据\\S2A\\S2A_MSIL2A_20230411T024531_N0510_R132_T49QGD_20240826T231833.SAFE\\GRANULE\\L2A_T49QGD_A040741_20230411T025901\\IMG_DATA\\R20m\\T49QGD_20230411T024531_B04_20m.jp2");
        System.out.println(Jp2ToGeoTiff(file));
    }
}

