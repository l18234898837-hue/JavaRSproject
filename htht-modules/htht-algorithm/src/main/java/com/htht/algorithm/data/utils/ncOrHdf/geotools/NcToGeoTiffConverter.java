package com.htht.algorithm.data.utils.ncOrHdf.geotools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.htht.algorithm.data.utils.ncOrHdf.NcFileUtils;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;
import org.gdal.osr.SpatialReference;
import org.opengis.feature.IllegalAttributeException;

import com.basic.netcdf2.nc.reader.model.Bound;
import com.basic.netcdf2.nc.reader.model.NcResult;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import ucar.nc2.NetcdfFile;

@Slf4j
public class NcToGeoTiffConverter {

    static {
        // 初始化GDAL库
        gdal.AllRegister();
    }

    /**
     * 将NetCDF文件转换为GeoTIFF格式
     * @param ncFilePath 输入NC文件路径
     * @param outputFilePath 输出文件路径（如果是单个变量，直接使用；如果是多个变量，作为基础路径）
     * @param variableName 变量名（如果为null，则转换所有变量）
     * @return 生成的GeoTIFF文件路径列表
     */
    public static List<String> ncToGeoTiff(String ncFilePath, String outputFilePath, String variableName) {
        List<String> resultFiles = new ArrayList<>();

        try {
            // 使用工具类打开NC文件
            NetcdfFile ncfile = NcFileUtils.openNetcdfFile(ncFilePath);

            // 获取维度信息和偏移量
            float offset = NcFileUtils.getCoordinateOffset(ncfile);
            String[] dimensionNames = NcFileUtils.getDimensionNames(ncfile);

            // 识别变量
            List<String> variableInfos;
            if (variableName != null && !variableName.isEmpty()) {
                // 如果指定了变量名，只处理该变量
                String longName = NcFileUtils.getVariableUnit(ncfile, variableName);
//                variableInfos = List.of(variableName + "," + longName);
                variableInfos = Arrays.asList(variableName + "," + longName);
            } else {
                // 否则处理所有变量
                variableInfos = NcFileUtils.identifyDataVariables(ncfile);
            }
            // 获取输出目录和基础文件名
            File outputFile = new File(outputFilePath);
            String outputDir = outputFile.getParent();
            String baseFileName = FileUtil.getPrefix(outputFile.getName());

            // 确保输出目录存在
            NcFileUtils.ensureDirectoryExists(outputDir);

            // 处理每个变量
            for (String varInfo : variableInfos) {
                String[] varParts = NcFileUtils.parseVariableInfo(varInfo);
                String currentVarName = varParts[0];
                String currentVarLongName = varParts.length > 1 ? varParts[1] : currentVarName;

                // 获取变量数据
                NcResult result = NcFileUtils.getVariableResult(ncFilePath, currentVarName, offset, dimensionNames);
                if (result != null) {
                    // 构建具体的输出文件路径
                    String finalOutputPath;
                    if (variableInfos.size() == 1) {
                        // 单个变量：使用用户指定的完整路径
                        finalOutputPath = outputFilePath;
                    } else {
                        // 多个变量：在指定路径基础上添加变量名
                        String fileName = String.format("%s_%s.tif", baseFileName, currentVarName);
                        finalOutputPath = outputDir + File.separator + fileName;
                    }
                    // 创建GeoTIFF
                    String createdFilePath = createGeoTiffFromNcResult(
                            result,
                            finalOutputPath,
                            currentVarName,
                            currentVarLongName,
                            ncfile
                    );
                    if (createdFilePath != null) {
                        resultFiles.add(createdFilePath);
                        log.info("成功创建: {}", createdFilePath);
                    }
                }
            }
            ncfile.close();
        } catch (Exception e) {
            log.error("转换NC文件失败: {}", ncFilePath, e);
            throw new IllegalAttributeException(ncFilePath + "转换NC文件失败，错误信息:" + e.getMessage());
        }

        return resultFiles;
    }

    /**
     * 从NcResult创建GeoTIFF文件
     */
    public static String createGeoTiffFromNcResult(NcResult result, String outputFilePath,
            String varName, String varLongName, NetcdfFile ncfile) throws Exception {
        if (result == null) {
            log.error("NcResult为空，无法创建GeoTIFF");
            return null;
        }

        Bound bound = result.getBound();
        float[][] data = result.getDatas();

        if (data == null || data.length == 0 || data[0].length == 0) {
            log.error("数据矩阵为空，无法创建GeoTIFF");
            return null;
        }

        int rows = data.length;
        int cols = data[0].length;

        // 确保输出目录存在
        NcFileUtils.ensureDirectoryExists(outputFilePath);

        log.info("创建GeoTIFF: {}，尺寸: {}x{}", outputFilePath, rows, cols);
        log.info("地理范围: 经度 [{}, {}]，纬度 [{}, {}]，分辨率: {}",
                bound.getLngStart(), bound.getLngEnd(),
                bound.getLatStart(), bound.getLatEnd(),
                bound.getDegreeOffset());

        try {
            // 1. 获取NoData值
            float fillValue = NcFileUtils.getFillValue(ncfile, varName);
            if (Float.isNaN(fillValue)) {
                fillValue = Float.NaN; // 默认NoData值
                log.warn("未找到变量的填充值，使用默认值: {}", fillValue);
            }

            // 2. 获取变量单位
            String unit = NcFileUtils.getVariableUnit(ncfile, varName);
            if (unit.isEmpty()) {
                unit = "unknown";
            }

            // 3. 创建GDAL数据集
            Driver driver = gdal.GetDriverByName("GTiff");
            if (driver == null) {
                log.error("无法获取GTiff驱动");
                return null;
            }

            // 4. 创建数据集（1个波段，32位浮点型）
            Dataset dataset = driver.Create(outputFilePath, cols, rows, 1, gdalconstConstants.GDT_Float32);
            if (dataset == null) {
                log.error("无法创建数据集");
                return null;
            }

            // 5. 设置地理变换参数
            double pixelWidth = bound.getDegreeOffset();
            double pixelHeight = bound.getDegreeOffset();

            // 计算左上角坐标
            double topLeftX = bound.getLngStart() ;
            double topLeftY = bound.getLatEnd() ;

            // 设置地理变换
            double[] geoTransform = {
                topLeftX,    // 左上角经度
                pixelWidth,  // 经度方向像素分辨率
                0,           // 旋转（通常为0）
                topLeftY,    // 左上角纬度
                0,           // 旋转（通常为0）
                - pixelHeight // 纬度方向像素分辨率（负值）
            };
            dataset.SetGeoTransform(geoTransform);

            // 6. 设置投影（WGS84）
            SpatialReference srs = new SpatialReference();
            srs.ImportFromEPSG(4326); // WGS84坐标系
            dataset.SetProjection(srs.ExportToWkt());

            // 7. 获取波段并写入数据
            Band band = dataset.GetRasterBand(1);

            int writeResult = 1;
            float[][] datas = (float[][]) data;
            int rowIndex = 0;
            for (float[] rowData : datas) {
//            	xoff = 0：从第1列开始写入
//            	yoff = 0：从第1行开始写入
//            	xsize = cols：写入cols列数据
//            	ysize = rows：写入rows行数据
                writeResult = band.WriteRaster(0, rowIndex, cols, 1, rowData);
                if (writeResult != gdalconstConstants.CE_None) {
                    log.error("写入第{}行数据失败", rowIndex);
                    break;
                }
                rowIndex++;
            }
            // 写入数据
//            if (writeResult != gdalconstConstants.CE_None) {
//                log.error("写入数据失败");
//                dataset.delete();
//                return null;
//            }

            // 8. 设置NoData值
            band.SetNoDataValue(fillValue);

            // 9. 设置波段统计信息
            band.FlushCache();
            double[] minMax = new double[2];
            band.ComputeRasterMinMax(minMax);
            band.SetStatistics(minMax[0], minMax[1], 0, 0);

            // 10. 设置波段描述和元数据
            band.SetDescription(varLongName);
            List<Map<String, Object>> attributes = NcFileUtils.getVariableAttributes(ncfile, varName);
            band.SetMetadataItem("parameterNumberName", varName, "");
            for (Map<String, Object> map : attributes) {
            	//dataset设置全局， 设置波段
            	band.SetMetadataItem(String.valueOf(map.get("name")),String.valueOf(map.get("value")), "");
			}
//            dataset.SetMetadataItem("NC_VARIABLE", varName, "");
//            dataset.SetMetadataItem("NC_UNIT", unit, "");
//            dataset.SetMetadataItem("NC_FILL_VALUE", String.valueOf(fillValue), "");

            // 11. 保存并关闭数据集
            dataset.FlushCache();
            dataset.delete();

            log.info("GeoTIFF创建成功: {}", outputFilePath);

            // 12. 创建金字塔（可选）
            createPyramids(outputFilePath);

            return outputFilePath;

        } catch (Exception e) {
            log.error("创建GeoTIFF文件失败: {}", outputFilePath, e);
            // 清理可能创建的不完整文件
            File file = new File(outputFilePath);
            if (file.exists()) {
                if (!file.delete()) {
                    log.warn("无法删除不完整的文件: {}", outputFilePath);
                }
            }
            throw e;
        }
    }

    /**
     * 为GeoTIFF文件创建金字塔
     */
    private static void createPyramids(String tiffFilePath) {
        try {
            log.info("为文件创建金字塔: {}", tiffFilePath);

            Dataset dataset = gdal.Open(tiffFilePath, gdalconstConstants.GA_Update);
            if (dataset == null) {
                log.warn("无法打开文件创建金字塔: {}", tiffFilePath);
                return;
            }

            int[] overviewLevels = {2, 4, 8, 16, 32};
            dataset.BuildOverviews("AVERAGE", overviewLevels);
            dataset.FlushCache();
            dataset.delete();

            log.info("金字塔创建完成: {}", tiffFilePath);
        } catch (Exception e) {
            log.warn("创建金字塔时发生错误: {}", e.getMessage());
        }
    }

    // 主方法示例
    public static void main(String[] args) {
        String ncFilePath = "D:\\file\\geoServerFile\\SST\\SST_RS_1_20220101_20220101_day_4km_000.nc";
        List<String> result1 = ncToGeoTiff(ncFilePath, "D:\\file\\geoServerFile\\SST\\SST_RS_1_20220101_20220101_day_4km_000.tif", null);

        // 转换所有变量到指定目录下的多个文件
//        String ncFilePath = "D:\\file\\geoServerFile\\SSHU\\SSHU_RS_1_20220102_20220102_day_0.25deg_000.nc";
//        List<String> result1 = ncToGeoTiff(ncFilePath, "D:\\file\\geoServerFile\\SSHU\\SSHU_RS_1_20220102_20220102_day_0.25deg_000.tif", null);
        for (String s : result1) {
			System.out.println(s);
		}
        // 只转换特定变量到指定文件
//        List<String> result2 = convertToGeoTiff(ncFilePath, "F:/output/geotiff/temperature.tif", "temperature");
    }
}
