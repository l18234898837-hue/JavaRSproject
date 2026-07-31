package com.htht.algorithm.data.utils.ncOrHdf.gdal;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.util.SimpleInternationalString;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import cn.hutool.core.date.LocalDateTimeUtil;
import lombok.extern.slf4j.Slf4j;

import javax.media.jai.RasterFactory;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeoTiffUtil {

    /**
     * 创建带有元数据的GridCoverage2D
     */
    public static GridCoverage2D createCoverageWithMetadata(float[][] data,
                                                            double topLeftX, double topLeftY,
                                                            double pixelSizeX, double pixelSizeY,
                                                            CoordinateReferenceSystem crs,
                                                            Map<String, Object> metadata) {
        int height = data.length;
        int width = data[0].length;

//		// 创建栅格数据
//		WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT, width, height, 1, null);
//
//		for (int y = 0; y < height; y++) {
//			for (int x = 0; x < width; x++) {
//				raster.setSample(x, y, 0, data[y][x]);
//			}
//		}

        // 创建仿射变换
//        AffineTransform2D affine = new AffineTransform2D(pixelSizeX, 0, 0, -pixelSizeY, topLeftX, topLeftY);
        // 创建网格几何
//        GridGeometry2D gridGeometry = new GridGeometry2D(new GridEnvelope2D(0, 0, width, height), affine, crs);

        double minY = topLeftY + height * pixelSizeY;
        double maxY = topLeftY;
        double maxX = topLeftX + width * pixelSizeX;
        double minX = topLeftX;

        ReferencedEnvelope envelope = new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
        // 创建覆盖层
        GridCoverageFactory factory = new GridCoverageFactory();
        GridCoverage2D coverage = factory.create("coverage", data, envelope);

//        coverage = factory.create("coverage",
//                coverage.getRenderedImage(),
//                envelope,
//                coverage.getSampleDimensions(),
//                null,
//                metadata);

        // 安全地设置元数据 数据元似乎只能在内存中，不能持久化到文件，不搞了
//        if (metadata != null && !metadata.isEmpty()) {
//            setCoverageMetadataSafely(coverage, metadata);
//        }

        return coverage;
    }

    /**
     * 设置覆盖层元数据
     */
    private static void setCoverageMetadata(GridCoverage2D coverage, Map<String, Object> metadata) {
        try {
            // 创建自定义元数据属性
            Map<String, Object> properties = new HashMap<>();

            // 将元数据添加到属性中
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // 特殊处理一些标准元数据字段
                switch (key.toLowerCase()) {
                    case "title":
                    case "description":
                        properties.put(key, new SimpleInternationalString(value.toString()));
                        break;
                    default:
                        properties.put("user." + key, value);
                        break;
                }
            }
//            TODO 暂时实现不了 设置覆盖层属性
//            coverage.getProperties().putAll(properties);

        } catch (Exception e) {
            throw new RuntimeException("设置覆盖层元数据失败", e);
        }
    }

    /**
     * 写入带有元数据的GeoTIFF文件
     */
    public static void writeGeoTiffWithMetadata(GridCoverage2D coverage,
                                                String outputFilePath,
                                                Map<String, Object> metadata) throws Exception {
        // 设置元数据到覆盖层
        if (metadata != null && !metadata.isEmpty()) {
            setCoverageMetadata(coverage, metadata);
        }

        // 写入文件
        GeoTiffUtil.writeGeoTiff(coverage, outputFilePath);
    }

    /**
     * 创建TIFF元数据属性映射
     */
    public static Map<String, Object> createTiffMetadata(String variableName,
                                                         String longName,
                                                         String unit,
                                                         Float fillValue,
                                                         List<Map<String, Object>> attributes) {
        Map<String, Object> metadata = new HashMap<>();

        // 基本元数据
        metadata.put("TIFFTAG_IMAGEDESCRIPTION", longName);
        metadata.put("TIFFTAG_DOCUMENTNAME", variableName);

        // 科学数据元数据
        metadata.put("variable_name", variableName);
        metadata.put("long_name", longName);
        metadata.put("units", unit != null ? unit : "unknown");

        if (fillValue != null && !Float.isNaN(fillValue)) {
            metadata.put("_FillValue", fillValue);
            metadata.put("missing_value", fillValue);
        }

        // 添加自定义属性
        if (attributes != null) {
            for (Map<String, Object> attr : attributes) {
                String name = String.valueOf(attr.get("name"));
                Object value = attr.get("value");
                metadata.put(name, value);
            }
        }

        // 设置创建信息
        metadata.put("creator", "NcToGeoTiffConverter");
        metadata.put("creation_date", LocalDateTimeUtil.formatNormal(LocalDateTime.now()));
        metadata.put("software", "GeoTools");

        return metadata;
    }

    /**
     * 读取GeoTIFF文件
     */
    public static GridCoverage2D readGeoTiff(String tiffFilePath) throws Exception {
        File tiffFile = new File(tiffFilePath);
        if (!tiffFile.exists()) {
            throw new IllegalArgumentException("GeoTIFF文件不存在: " + tiffFilePath);
        }

        GeoTiffReader reader = new GeoTiffReader(tiffFile);
        try {
            return reader.read(null);
        } finally {
            reader.dispose();
        }
    }

    /**
     * 写入GeoTIFF文件
     */
    public static void writeGeoTiff(GridCoverage2D coverage, String outputFilePath) throws Exception {
        File outputFile = new File(outputFilePath);
        GeoTiffWriter writer = new GeoTiffWriter(outputFile);
        try {
            GeoTiffWriteParams writeParams = new GeoTiffWriteParams();
            writeParams.setCompressionMode(GeoTiffWriteParams.MODE_EXPLICIT);
            writeParams.setCompressionType("LZW"); // 使用 LZW 无损压缩
//            writeParams.setTilingMode(GeoTiffWriteParams.MODE_EXPLICIT);
//            writeParams.setTiling(256, 256); // 设置瓦片大小

            // 将参数包装为 GeoTools 需要的格式
            ParameterValueGroup params = new GeoTiffFormat().getWriteParameters();
            params.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(writeParams);
            writer.write(coverage, params.values().toArray(new GeneralParameterValue[1]));
        } finally {
            writer.dispose();
        }
    }

    /**
     * 从浮点数组创建GridCoverage2D
     */
    public static GridCoverage2D createCoverageFromFloatData(float[][] data,
                                                             double topLeftX, double topLeftY,
                                                             double pixelSize,
                                                             CoordinateReferenceSystem crs) {
        return createCoverageFromFloatData(data, topLeftX, topLeftY, pixelSize, -pixelSize, crs);
    }

    /**
     * 从浮点数组创建GridCoverage2D
     */
    public static GridCoverage2D createCoverageFromFloatData(float[][] data,
                                                             double topLeftX, double topLeftY,
                                                             double pixelSizeX, double pixelSizeY,
                                                             CoordinateReferenceSystem crs) {
        int height = data.length;
        int width = data[0].length;

        // 创建栅格数据
        WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT, width, height, 1, null);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.setSample(x, y, 0, data[y][x]);
            }
        }

        // 创建仿射变换
        AffineTransform2D affine = new AffineTransform2D(pixelSizeX, 0, 0, pixelSizeY, topLeftX, topLeftY);

        // 创建网格几何
        GridGeometry2D gridGeometry = new GridGeometry2D(new GridEnvelope2D(0, 0, width, height), affine, crs);

        // 创建覆盖层 - 修复后的代码
        GridCoverageFactory factory = new GridCoverageFactory();
        return factory.create("coverage", raster, gridGeometry.getEnvelope(), null);
    }

    /**
     * 提取地理变换信息
     */
    public static Map<String, Object> extractGeoTransformInfo(GridCoverage2D coverage) {
        Map<String, Object> geoInfo = new HashMap<>();
        if (coverage == null) {
            return geoInfo;
        }

        try {
            // 1. 读取基本信息
            String name = coverage.getName().toString();
            geoInfo.put("name", name);

            GridGeometry2D gridGeometry = coverage.getGridGeometry();
            GridEnvelope gridRange = gridGeometry.getGridRange();

            int width = gridRange.getSpan(0);
            int height = gridRange.getSpan(1);
            geoInfo.put("width", width);
            geoInfo.put("height", height);

            // 2. 读取地理范围
            ReferencedEnvelope envelope = new ReferencedEnvelope(coverage.getEnvelope());
            double minX = envelope.getMinX();
            double maxX = envelope.getMaxX();
            double minY = envelope.getMinY();
            double maxY = envelope.getMaxY();

            geoInfo.put("minX", minX);
            geoInfo.put("maxX", maxX);
            geoInfo.put("minY", minY);
            geoInfo.put("maxY", maxY);

            double topLeftX = minX;
            double topLeftY = maxY;

            // 3. 获取坐标参考系统
            CoordinateReferenceSystem crs = gridGeometry.getCoordinateReferenceSystem();
            if (crs != null) {
                String crsName = crs.getName().toString();
                geoInfo.put("crsName", crsName);
                geoInfo.put("projection", crs.toWKT());

                try {
                    Integer epsgCode = CRS.lookupEpsgCode(crs, true);
                    if (epsgCode != null) {
                        geoInfo.put("epsg", epsgCode);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 4. 读取栅格数据信息
            RenderedImage image = coverage.getRenderedImage();
            Raster raster = image.getData();

            int numBands = raster.getNumBands();
            int dataType = raster.getDataBuffer().getDataType();

            geoInfo.put("numBands", numBands);
            geoInfo.put("dataType", dataType);

            // 5. 获取地理变换
            MathTransform gridToCRS = gridGeometry.getGridToCRS();
            if (gridToCRS instanceof AffineTransform2D) {
                AffineTransform2D affine = (AffineTransform2D) gridToCRS;

                double[] geoTransform = new double[6];
                geoTransform[0] = affine.getTranslateX();
                geoTransform[1] = affine.getScaleX();
                geoTransform[2] = affine.getShearX();
                geoTransform[3] = affine.getTranslateY();
                geoTransform[4] = affine.getShearY();
                geoTransform[5] = affine.getScaleY();

                geoInfo.put("geoTransform", geoTransform);

                // 提取变量
//                double topLeftX = geoTransform[0];
//                double topLeftY = geoTransform[3];
                double pixelSizeX = geoTransform[1];
                double pixelSizeY = geoTransform[5];

                // 计算边界坐标
                double bottomRightX = topLeftX + width * pixelSizeX;
                double bottomRightY = topLeftY + height * pixelSizeY;

                geoInfo.put("topLeftX", topLeftX);
                geoInfo.put("topLeftY", topLeftY);
                geoInfo.put("pixelSizeX", pixelSizeX);
                geoInfo.put("pixelSizeY", pixelSizeY);
                geoInfo.put("bottomRightX", bottomRightX);
                geoInfo.put("bottomRightY", bottomRightY);
                geoInfo.put("lngStart", Math.min(topLeftX, bottomRightX));
                geoInfo.put("lngEnd", Math.max(topLeftX, bottomRightX));
                geoInfo.put("latStart",topLeftY);
                geoInfo.put("latEnd", bottomRightY);
                geoInfo.put("resolution", Math.abs(pixelSizeX));

                // 添加方向标志
                boolean isNorthUp = pixelSizeY < 0;  // 通常pixelSizeY为负表示北在上
                geoInfo.put("isNorthUp", isNorthUp);
                geoInfo.put("dataOrientation", isNorthUp ? "north_up" : "south_up");
            }

            // 7. 读取元数据
//            Map<String, Object> properties = coverage.getProperties();
//            if (properties != null && !properties.isEmpty()) {
//                geoInfo.put("properties", properties);
//
//            }

        } catch (Exception e) {
            throw new RuntimeException("提取地理变换信息失败", e);
        }

        return geoInfo;
    }

    /**
     * 读取波段数据（GeoTools版本）
     */
    public static float[][] readBandData(Raster raster, int bandIndex,
                                         int width, int height) {
        float[][] data = new float[height][width];

        try {
            SampleModel sampleModel = raster.getSampleModel();
            int dataType = sampleModel.getDataType();

            // 统一使用int[]接收数据
            int[] intPixels = new int[width * height];
            intPixels = raster.getSamples(0, 0, width, height, bandIndex, intPixels);

            // 根据数据类型进行转换
            switch (dataType) {
                case DataBuffer.TYPE_BYTE:
                    // 字节型数据：转换为0-255
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = intPixels[index] & 0xFF;
                        }
                    }
                    break;

                case DataBuffer.TYPE_USHORT:
                    // 无符号短整型：转换为0-65535
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = intPixels[index] & 0xFFFF;
                        }
                    }
                    break;

                case DataBuffer.TYPE_SHORT:
                    // 短整型：直接转换
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = (short) intPixels[index];
                        }
                    }
                    break;

                case DataBuffer.TYPE_INT:
                    // 整型：直接转换
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = intPixels[index];
                        }
                    }
                    break;

                case DataBuffer.TYPE_FLOAT:
                    // 如果已经是浮点型，使用getSamples的float版本
//                    float[] floatPixels = raster.getSamples(0, 0, width, height, bandIndex, (float[]) null);
//                    for (int row = 0; row < height; row++) {
//                        System.arraycopy(floatPixels, row * width, data[row], 0, width);
//                    }
                	float[] floatPixels = raster.getSamples(0, 0, width, height, bandIndex, (float[]) null);
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = floatPixels[index];
                        }
                    }
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    // 双精度浮点型
                    double[] doublePixels = raster.getSamples(0, 0, width, height, bandIndex, (double[]) null);
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = (float) doublePixels[index];
                        }
                    }
                    break;

                default:
                    // 默认处理：直接使用int值
                    log.warn("不支持的数据类型: {}，使用默认处理", dataType);
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = row * width + col;
                            data[row][col] = intPixels[index];
                        }
                    }
                    break;
            }

            log.info("波段数据读取完成: {}x{}，数据类型: {}", height, width, dataType);

        } catch (Exception e) {
            log.error("读取波段数据失败", e);
        }

        return data;
    }

    /**
     * 获取波段数量
     */
    public static int getBandCount(GridCoverage2D coverage) {
        return coverage.getRenderedImage().getSampleModel().getNumBands();
    }

    /**
     * 验证GeoTIFF文件
     */
    public static boolean validateGeoTiff(String tiffFilePath) {
        try {
            GridCoverage2D coverage = readGeoTiff(tiffFilePath);
            GridEnvelope gridRange = coverage.getGridGeometry().getGridRange();

            int width = gridRange.getSpan(0);
            int height = gridRange.getSpan(1);

            if (width <= 0 || height <= 0) {
                return false;
            }

            CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem();
            return crs != null;

        } catch (Exception e) {
            return false;
        }
    }
}
