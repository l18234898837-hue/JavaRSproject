package com.htht.algorithm.data.utils.ncOrHdf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.basic.netcdf2.nc.reader.LatLngDataReader;
import com.basic.netcdf2.nc.reader.model.Bound;
import com.basic.netcdf2.nc.reader.model.NcResult;
import com.basic.netcdf2.nc.reader.options.LatLngOptions;
import com.htht.algorithm.data.utils.ncOrHdf.lib.*;

import lombok.extern.slf4j.Slf4j;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;

@Slf4j
public class NcToJsonConverter {

    /**
     * 解析NC文件并转换为JSON格式（支持多个变量，输出为JSON数组）
     *
     * @param ncFilePath NC文件路径
     * @param outputFilePath 输出文件完整路径
     * @return 生成的JSON文件路径
     */
    public static String parseNcToJson(String ncFilePath, String outputFilePath) {
        File ncFile = new File(ncFilePath);
        if (!ncFile.exists()) {
            log.error("NC文件不存在: {}", ncFilePath);
            return null;
        }
        try (NetcdfFile ncfile = NetcdfFiles.open(ncFilePath)) {
            // 获取所有候选变量
            List<String> candidateVariables = NcFileUtils.identifyDataVariables(ncfile);
            if (candidateVariables == null || candidateVariables.isEmpty()) {
                log.error("未找到有效的变量，请检查NC文件结构");
                return null;
            }
            log.info("识别到 {} 个变量: {}", candidateVariables.size(), candidateVariables);
            float offset = NcFileUtils.getCoordinateOffset(ncfile);
            String dimensions = NcFileUtils.getDimensions(ncfile);
            String[] dimensionNames = dimensions.split(",");

            // 确保输出目录存在
            ensureDirectoryExists(outputFilePath);
            // 处理单个变量和多个变量的不同情况
            if (candidateVariables.size() == 1) {
                // 单个变量：保持原有格式
                return processSingleVariable(ncFilePath, outputFilePath, candidateVariables.get(0), offset, dimensionNames, ncfile);
            } else {
                // 多个变量：输出JSON数组格式
                return processMultipleVariables(ncFilePath, outputFilePath, candidateVariables, offset, dimensionNames, ncfile);
            }

        } catch (Exception e) {
        	e.printStackTrace();
            log.error("解析NC文件失败: {}", ncFilePath, e);
            throw new IllegalArgumentException(ncFilePath + "解析NC文件失败，错误信息: " + e.getMessage());
        }
    }

    /**
     * 处理单个变量情况
     * @param ncFilePath
     * @param outputFilePath
     * @param variableInfo
     * @param offset
     * @param dimensionNames
     * @return
     */
    private static String processSingleVariable(String ncFilePath, String outputFilePath,
                                              String variableInfo, float offset, String[] dimensionNames, NetcdfFile ncfile) {
        try {
            // 解析变量信息
            String[] varParts = variableInfo.split(",", 2);
            String variableName = varParts[0];
            String longName = varParts.length > 1 ? varParts[1] : variableName;

         // 获取变量属性信息
            List<Map<String, Object>> attributes = NcFileUtils.getVariableAttributes(ncfile, variableName);
            log.info("处理单个变量: {} ({})", variableName, longName);
            LatLngOptions options = NcFileUtils.createLatLngOptions(variableName, offset, dimensionNames);
            LatLngDataReader latLngDataReader = new LatLngDataReader(ncFilePath, options);
            while (latLngDataReader.hasNext()) {
                NcResult next = latLngDataReader.next();
                Bound bound = next.getBound();
                JsonCreatorBorder jsonCreatorBorder = new JsonCreatorBorder(
                    bound.getLatStart(), bound.getLatEnd(), bound.getLngStart(),
                    bound.getLngEnd(), bound.getDegreeOffset(), bound.sizeX, bound.sizeY,
                    longName, variableName, attributes
                );
                // 创建单个JSON文件
                createJsonFile(next.getDatas(), jsonCreatorBorder, outputFilePath);
                log.info("单个变量JSON文件已生成: {}", outputFilePath);
                return outputFilePath;
            }
        } catch (Exception e) {
            log.error("处理单个变量时发生错误", e);
        }
        return null;
    }

    /**
     * 处理多个变量情况，输出JSON数组
     * @param ncFilePath
     * @param outputFilePath
     * @param candidateVariables
     * @param offset
     * @param dimensionNames
     * @return
     */
    private static String processMultipleVariables(String ncFilePath, String outputFilePath,
                                                  List<String> candidateVariables, float offset,
                                                  String[] dimensionNames, NetcdfFile ncfile) {
        try {
            List<JsonCreatorFlowBean> jsonCreatorArray = new ArrayList<>();

            // 处理每个变量
            for (String variableInfo : candidateVariables) {
            	JsonCreatorFlowBean jsonCreatorFlowBean = new JsonCreatorFlowBean();
                // 解析变量信息
                String[] varParts = variableInfo.split(",", 2);
                String variableName = varParts[0];
                String parameterCategoryName = varParts.length > 1 ? varParts[1] : variableName;
                List<Map<String, Object>> attributes = NcFileUtils.getVariableAttributes(ncfile, variableName);
                log.info("处理变量: {} ({})", variableName, parameterCategoryName);
                try {
                    LatLngOptions options = NcFileUtils.createLatLngOptions(variableName, offset, dimensionNames);
                    LatLngDataReader latLngDataReader = new LatLngDataReader(ncFilePath, options);
                    while (latLngDataReader.hasNext()) {
                        NcResult next = latLngDataReader.next();
                        Bound bound = next.getBound();
                        // 创建变量数据对象
                        JsonCreatorBorder jsonCreatorBorder = new JsonCreatorBorder(bound.getLatStart(), bound.getLatEnd(), bound.getLngStart(),
                                bound.getLngEnd(), bound.getDegreeOffset(), bound.sizeX, bound.sizeY, parameterCategoryName, variableName, attributes);
                        JsonCreatorHeader header = JsonCreatorHeader.border2Header(jsonCreatorBorder);
                        jsonCreatorFlowBean.setHeader(header);
                        jsonCreatorFlowBean.setDatas(next.getDatas());
                        jsonCreatorArray.add(jsonCreatorFlowBean);
                    }
                } catch (Exception e) {
                    log.error("处理变量 {} 时发生错误", variableName, e);
                }
            }
            // 创建JSON数组文件
            createJsonArrayFile(jsonCreatorArray, outputFilePath);
            log.info("多个变量JSON数组文件已生成: {}", outputFilePath);
            return outputFilePath;

        } catch (Exception e) {
            log.error("处理多个变量时发生错误", e);
            return null;
        }
    }

    /**
     * 创建JSON数组文件
     * @param jsonCreatorArray
     * @param outputFilePath
     */
    private static void createJsonArrayFile(List<JsonCreatorFlowBean> jsonCreatorArray, String outputFilePath) {
    	try {
            // 这里需要根据您的JSON库来创建数组格式的JSON文件
            JsonArrayCreator arrayCreator = new JsonArrayCreator(jsonCreatorArray);
            arrayCreator.create(outputFilePath);
            log.debug("JSON数组文件创建成功: {}", outputFilePath);
        } catch (Exception e) {
            log.error("创建JSON数组文件失败: {}", outputFilePath, e);
        }
	}

    /**
     * 创建单个JSON文件
     * @param datas
     * @param jsonCreatorBorder
     * @param outputFilePath
     */
    private static void createJsonFile(float[][] datas, JsonCreatorBorder jsonCreatorBorder, String outputFilePath) {
        try {
            JsonCreator jsonCreator = new JsonCreator(datas, jsonCreatorBorder);
            jsonCreator.create(outputFilePath);
            log.debug("JSON文件创建成功: {}", outputFilePath);
        } catch (Exception e) {
            log.error("创建JSON文件失败: {}", outputFilePath, e);
        }
    }

    /**
     * 确保目录存在
     * @param filePath
     * @throws IOException
     */
    private static void ensureDirectoryExists(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.debug("已创建目录: {}", parentDir);
        }
    }

    /**
     * sample
     */
    public static void main(String[] args) {
//        String ncFilePath = "F:\\南海所项目\\剖面渲染\\2025-11\\CBH_ME_7_20251118_20251118_1h_0.25deg_012.nc";
//        String outputFilePath = "F:/output/json/CBH_ME_7_20251118_20251118_1h_0.25deg_012.json";
      String ncFilePath = "D:\\file\\geoServerFile\\WINDDIR\\SSW_RS_1_20220101_20220101_06h_12.5km_001-offshoredir.nc";
      String outputFilePath = "D:\\file\\geoServerFile\\SST\\SST_RS_1_20220101_20220101_day_4km_000.json";

    	// 经纬度都是2d数据， 兼容起来比较麻烦
//      String ncFilePath = "F:\\南海所项目\\岛礁数据\\data to gdhtht\\3数值预报\\2022060500_sound_MJJ_2000.nc";
//      String outputFilePath = "F:/output/json/2022060500_sound_MJJ_2000.json";

        String jsonFile = parseNcToJson(ncFilePath, outputFilePath);
        if (jsonFile != null) {
            System.out.println("转换成功: " + jsonFile);
        } else {
            System.out.println("转换失败");
        }
    }
}
