package com.htht.algorithm.data.utils.ncOrHdf.gdal;

import com.htht.algorithm.data.enums.NcFileType;
import com.htht.algorithm.data.utils.ncOrHdf.NcFileUtils;
import lombok.extern.slf4j.Slf4j;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.basic.netcdf2.nc.reader.model.Bound;
import com.basic.netcdf2.nc.reader.model.NcResult;

import ucar.nc2.NetcdfFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class NcToGeoTiffConverter {

    /**
     * 将NetCDF文件转换为GeoTIFF格式（使用GeoTools实现）
     */
    public static List<String> ncToGeoTiff(String ncFilePath, String outputFilePath, String variableName) {
        List<String> resultFiles = new ArrayList<>();

        try {
            NetcdfFile ncfile = NcFileUtils.openNetcdfFile(ncFilePath);
            float offset;
            if(variableName!=null&&variableName.equals("Grid/SWH_g")){
                offset = NcFileUtils.getCoordinateOffset(ncfile,"Grid/lon_g");
            }else{
                offset = NcFileUtils.getCoordinateOffset(ncfile);
            }
//            String[] dimensionNames = NcFileUtils.getDimensionNames(ncfile);

            List<String> variableInfos;
            if (variableName != null && !variableName.isEmpty()) {
                String longName = NcFileUtils.getVariableUnit(ncfile, variableName);
                variableInfos = Arrays.asList(variableName + "," + longName);
            } else {
                variableInfos = NcFileUtils.identifyDataVariables(ncfile);
            }

            File outputFile = new File(outputFilePath);
            String outputDir = outputFile.getParent();
            String baseFileName = getFilePrefix(outputFile.getName());

            NcFileUtils.ensureDirectoryExists(outputDir);

            for (String varInfo : variableInfos) {
                String[] varParts = NcFileUtils.parseVariableInfo(varInfo);
                String currentVarName = varParts[0];
                String currentVarLongName = varParts.length > 1 ? varParts[1] : currentVarName;

                  NcResult result = NcFileUtils.getVariableResult2(ncFilePath, currentVarName, offset, ncfile.getDimensions());
                if (result != null) {
                    String finalOutputPath;
                    if (variableInfos.size() == 1) {
                        finalOutputPath = outputFilePath;
                    } else {
                        String fileName = String.format("%s_%s.tif", baseFileName, currentVarName);
                        finalOutputPath = outputDir + File.separator + fileName;
                    }

                    String createdFilePath = createGeoTiffFromNcResultGeoTools(
                            result, finalOutputPath, currentVarName, currentVarLongName, ncfile);
                    if (createdFilePath != null) {
                        resultFiles.add(createdFilePath);
                        log.info("成功创建: {}", createdFilePath);
                    }
                }
            }
            ncfile.close();
        } catch (Exception e) {
            log.error("转换NC文件失败: {}", ncFilePath, e);
            e.printStackTrace();
            throw new RuntimeException(ncFilePath + "转换NC文件失败，错误信息:" + e.getMessage());
        }

        return resultFiles;
    }

    /**
     * 使用GeoTools从NcResult创建GeoTIFF文件（包含元数据）
     */
    public static String createGeoTiffFromNcResultGeoTools(NcResult result, String outputFilePath,
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

        NcFileUtils.ensureDirectoryExists(outputFilePath);

        log.info("创建GeoTIFF: {}，尺寸: {}x{}", outputFilePath, rows, cols);
        log.info("地理范围: 经度 [{}, {}]，纬度 [{}, {}]，分辨率: {}",
                bound.getLngStart(), bound.getLngEnd(),
                bound.getLatStart(), bound.getLatEnd(),
                bound.getDegreeOffset());

        try {
            // 获取元数据信息
            float fillValue = NcFileUtils.getFillValue(ncfile, varName);
            String unit = NcFileUtils.getVariableUnit(ncfile, varName);
            List<Map<String, Object>> attributes = NcFileUtils.getVariableAttributes(ncfile, varName);

            if (Float.isNaN(fillValue)) {
                fillValue = Float.NaN;
            }
            if (unit == null || unit.isEmpty()) {
                unit = "unknown";
            }

            // 获取WGS84坐标系
            CoordinateReferenceSystem crs = CRS.decode("EPSG:4326", true);

            // 计算左上角坐标和像素大小
//            double topLeftX = bound.getLngStart();
//            double topLeftY = bound.getLatEnd();
//            double pixelSize = bound.getDegreeOffset();
            double[] correctedBounds = calculateCorrectBounds(bound, cols, rows);
            double topLeftX = correctedBounds[0];
            double topLeftY = correctedBounds[1];
            double pixelSize = bound.getDegreeOffset();

            // 处理NoData值
            float[][] processedData = processNoDataValues(data, fillValue);

            // 创建元数据
            Map<String, Object> metadata = GeoTiffUtil.createTiffMetadata(
                varName, varLongName, unit, fillValue, attributes);

            // 使用GeoTiffMetadataUtil创建带元数据的覆盖层
            GridCoverage2D coverage = GeoTiffUtil.createCoverageWithMetadata(
                processedData, topLeftX, topLeftY, pixelSize, -pixelSize, crs, metadata);

            // 写入GeoTIFF文件
            GeoTiffUtil.writeGeoTiffWithMetadata(coverage, outputFilePath, metadata);

            log.info("GeoTIFF创建成功: {}", outputFilePath);
            return outputFilePath;

        } catch (Exception e) {
            log.error("创建GeoTIFF文件失败: {}", outputFilePath, e);
            File file = new File(outputFilePath);
            if (file.exists() && !file.delete()) {
                log.warn("无法删除不完整的文件: {}", outputFilePath);
            }
            throw e;
        }

    }

    /**
     * 避免半格偏移
     */
    private static double[] calculateCorrectBounds(Bound bound, int cols, int rows) {
        double pixelSize = bound.getDegreeOffset();

        // 修正左上角坐标，确保没有半格偏移
        double topLeftX = bound.getLngStart();
        double topLeftY = bound.getLatEnd();

        // 如果坐标有半格偏移，进行修正
        if (Math.abs(topLeftX % pixelSize) == pixelSize/2) {
            topLeftX = Math.round(topLeftX / pixelSize) * pixelSize;
        }
        if (Math.abs(topLeftY % pixelSize) == pixelSize/2) {
            topLeftY = Math.round(topLeftY / pixelSize) * pixelSize;
        }

        return new double[]{topLeftX, topLeftY};
    }

    /**
     * 处理NoData值
     */
    private static float[][] processNoDataValues(float[][] data, float fillValue) {
        int rows = data.length;
        int cols = data[0].length;
        float[][] processedData = new float[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float value = data[i][j];
                if (Float.isNaN(value) || value == fillValue) {
                    processedData[i][j] = Float.NaN;
                } else {
                    processedData[i][j] = value;
                }
            }
        }

        return processedData;
    }

    /**
     * 获取文件前缀（不含扩展名）
     */
    private static String getFilePrefix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    public static List<String> autoFileRun(String ncFilePath, String variableName){
        int splitIndex = ncFilePath.lastIndexOf(".");
        // 情况2：截取分隔符后到末尾的字符串（真正的文件名）
        String fileName = ncFilePath.substring(0,splitIndex);
        return ncToGeoTiff(ncFilePath, fileName+".tif", variableName);
    }



    // 主方法保持不变
    public static void main(String[] args) {
    	//TODO 元数据无法放进去，后面再研究

        //SSW运行不了
//        String ncFilePath = "D:\\file\\geoServerFile\\SWH\\SWH_RS_7_20240101_20240102_day_0.25d_000.nc";
//        String ncFilePath = "D:\\file\\geoServerFile\\WINDDIR_SAR\\SSW_RS_1_20220113_20220113_12h_1km_000-nearshoredir.nc";
//        String ncFilePath = "D:\\file\\geoServerFile\\WINDDIR\\SSW_RS_1_20220101_20220101_06h_12.5km_002-offshoredir.nc";
//        String ncFilePath = "D:\\file\\geoServerFile\\SLA\\SLA_RS_7_20220108_20220108_day_0.125deg_000.nc";

        // SLA
//        autoFileRun("D:\\file\\geoServerFile\\SLA\\SLA_RS_7_20220108_20220108_day_0.125deg_000.nc",null);
        // SSHU
//        autoFileRun("D:\\file\\geoServerFile\\SSHU\\SSHU_RS_1_20220101_20220101_day_0.25deg_000.nc",null);
        // SST
//        autoFileRun("D:\\file\\geoServerFile\\SST\\SST_RS_1_20220101_20220101_day_4km_000.nc",null);
        // SWH 失败
        autoFileRun("D:\\file\\geoServerFile\\SWH\\SWH_RS_7_20240104_20240105_day_0.25d_000.nc","Grid/SWH_g");
        // WINDDIR
//        autoFileRun("D:\\file\\geoServerFile\\WINDDIR\\SSW_RS_1_20220101_20220101_06h_12.5km_001-offshoredir.nc",null);
        // WINDDIR_SAR
//        autoFileRun("D:\\file\\geoServerFile\\WINDDIR_SAR\\SSW_RS_1_20220312_20220312_12h_1km_001-nearshoredir.nc","nearshoredir");
        // ZSD
//        autoFileRun("D:\\file\\geoServerFile\\ZSD\\ZSD_RS_1_20220101_20220101_day_4km_000.nc",null);


//        for (String s : result1) {
//            System.out.println(s);
//        }
    }
}
