package com.htht.algorithm.data.utils.ncOrHdf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;


@Slf4j
public class HdfToJsonConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 将HDF文件转换为JSON格式的元数据信息
     * @param hdfFilePath HDF文件路径
     * @param outputJsonPath 输出JSON文件路径（如果为null，则返回JSON字符串）
     * @param includeData 是否包含变量数据（谨慎使用，数据量大时JSON会很大）
     * @return JSON字符串或文件路径
     */
    @SuppressWarnings("deprecation")
	public static String hdfToJson(String hdfFilePath, String outputJsonPath, boolean includeData) {
        try (NetcdfFile hdfFile = NetcdfFile.open(hdfFilePath)) {
            // 构建HDF文件信息结构
            Map<String, Object> hdfInfo = buildHdfInfo(hdfFile, includeData);

            // 转换为JSON字符串
            String jsonString = objectMapper.writeValueAsString(hdfInfo);

            if (outputJsonPath != null) {
                // 写入文件
                writeJsonToFile(jsonString, outputJsonPath);
                log.info("HDF转JSON完成: {}", outputJsonPath);
                return outputJsonPath;
            } else {
                return jsonString;
            }

        } catch (Exception e) {
            log.error("HDF转JSON失败: {}", hdfFilePath, e);
            throw new RuntimeException("HDF转JSON失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建HDF文件信息结构
     */
    private static Map<String, Object> buildHdfInfo(NetcdfFile hdfFile, boolean includeData) {
        Map<String, Object> hdfInfo = new HashMap<>();

        // 文件基本信息
        hdfInfo.put("fileName", FilenameUtils.getBaseName(hdfFile.getLocation()));
        hdfInfo.put("fileSize", getFileSize(hdfFile.getLocation()));
        hdfInfo.put("format", hdfFile.getFileTypeId());
        // 全局属性
        hdfInfo.put("globalAttributes", getGlobalAttributes(hdfFile));
        // 维度信息
        hdfInfo.put("dimensions", getDimensionsInfo(hdfFile));
        // 变量信息
        hdfInfo.put("variables", getVariablesInfo(hdfFile, includeData));
        // 坐标系统信息
        hdfInfo.put("coordinateSystem", getCoordinateSystemInfo(hdfFile));
        // 数据统计信息
        hdfInfo.put("statistics", getFileStatistics(hdfFile));

        return hdfInfo;
    }

    /**
     * 获取文件大小信息
     */
    private static Map<String, Object> getFileSize(String filePath) {
        File file = new File(filePath);
        Map<String, Object> sizeInfo = new HashMap<>();
        sizeInfo.put("bytes", file.length());
        sizeInfo.put("humanReadable", formatFileSize(file.length()));
        return sizeInfo;
    }

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    /**
     * 获取全局属性信息
     */
    @SuppressWarnings("deprecation")
	private static List<Map<String, Object>> getGlobalAttributes(NetcdfFile hdfFile) {
        List<Map<String, Object>> attributes = new ArrayList<>();

        for (Attribute attr : hdfFile.getGlobalAttributes()) {
            Map<String, Object> attrInfo = new HashMap<>();
            attrInfo.put("name", attr.getShortName());
            attrInfo.put("type", attr.getDataType().toString());
            attrInfo.put("value", NcFileUtils.getAttributeValue(attr));
            attrInfo.put("length", attr.getLength());
            attributes.add(attrInfo);
        }

        return attributes;
    }

    /**
     * 获取维度信息
     */
    @SuppressWarnings("deprecation")
	private static List<Map<String, Object>> getDimensionsInfo(NetcdfFile hdfFile) {
        List<Map<String, Object>> dimensions = new ArrayList<>();

        for (Dimension dim : hdfFile.getDimensions()) {
            Map<String, Object> dimInfo = new HashMap<>();
            dimInfo.put("name", dim.getShortName());
            dimInfo.put("length", dim.getLength());
            dimInfo.put("unlimited", dim.isUnlimited());
            dimInfo.put("shared", dim.isShared());
            dimensions.add(dimInfo);
        }

        return dimensions;
    }

    /**
     * 获取变量信息
     */
    @SuppressWarnings("deprecation")
	private static List<Map<String, Object>> getVariablesInfo(NetcdfFile hdfFile, boolean includeData) {
        List<Map<String, Object>> variables = new ArrayList<>();

        for (Variable var : hdfFile.getVariables()) {
            Map<String, Object> varInfo = new HashMap<>();
            varInfo.put("name", var.getShortName());
            varInfo.put("description", var.getDescription());
            varInfo.put("dataType", var.getDataType().toString());
            varInfo.put("shape", var.getShape());
            varInfo.put("size", var.getSize());
            varInfo.put("dimensions", getVariableDimensions(var));
            varInfo.put("attributes", getVariableAttributes(var));
            varInfo.put("isCoordinateVariable", isCoordinateVariable(var.getShortName()));

            if (includeData) {
                try {
                    varInfo.put("data", getVariableDataSample(var));
                } catch (Exception e) {
                    log.warn("无法读取变量数据: {}", var.getShortName(), e);
                    varInfo.put("dataError", "读取失败: " + e.getMessage());
                }
            }
            variables.add(varInfo);
        }

        return variables;
    }

    /**
     * 判断是否为坐标变量
     */
    private static boolean isCoordinateVariable(String variableName) {
        String lowerName = variableName.toLowerCase();
        return lowerName.equals("lat") || lowerName.equals("lon") || lowerName.equals("latitude") ||
               lowerName.equals("longitude") || lowerName.equals("time") || lowerName.equals("lev") ||
               lowerName.equals("level") || lowerName.equals("depth") || lowerName.equals("number") ||
               lowerName.equals("expver");
    }

    /**
     * 获取变量维度信息
     */
    private static List<String> getVariableDimensions(Variable var) {
        List<String> dimNames = new ArrayList<>();
        for (Dimension dim : var.getDimensions()) {
            dimNames.add(dim.getShortName());
        }
        return dimNames;
    }

    /**
     * 获取变量属性信息
     */
    @SuppressWarnings("deprecation")
	private static List<Map<String, Object>> getVariableAttributes(Variable var) {
        List<Map<String, Object>> attributes = new ArrayList<>();

        for (Attribute attr : var.getAttributes()) {
            Map<String, Object> attrInfo = new HashMap<>();
            attrInfo.put("name", attr.getShortName());
            attrInfo.put("type", attr.getDataType().toString());
            attrInfo.put("value", NcFileUtils.getAttributeValue(attr));
            attributes.add(attrInfo);
        }

        return attributes;
    }

    /**
     * 获取变量数据样本（完整数据）
     */
    private static Object getVariableDataSample(Variable var) throws IOException {
        Array data = var.read();
        return convertArrayToAppropriateType(data, var.getDataType());
    }

    private static Object convertArrayToAppropriateType(Array array, DataType dataType) {
    	int rank = array.getRank();

        try {
            if (rank == 2) {
                return convertTo2DFloatArray(array, dataType);
            } else if (rank == 1) {
                return convertTo1DArray(array, dataType);
            } else {
                return convertArrayToObject(array);
            }
        } catch (Exception e) {
            log.warn("数据类型转换失败，返回字符串表示", e);
            return array.toString();
        }
	}

	private static Object convertTo1DArray(Array array, DataType dataType) {
		int length = array.getShape()[0];
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = array.getFloat(i);
        }
        return result;
	}

	private static Object convertTo2DFloatArray(Array array, DataType dataType) {
		int[] shape = array.getShape();
	    int rows = shape[0];
	    int cols = shape[1];
	    float[][] result = new float[rows][cols];
	    for (int i = 0; i < rows; i++) {
	        for (int j = 0; j < cols; j++) {
	            int index = i * cols + j;
	            switch (dataType) {
	                case FLOAT:
	                    result[i][j] = array.getFloat(index);
	                    break;
	                case DOUBLE:
	                    result[i][j] = (float) array.getDouble(index);
	                    break;
	                case INT:
	                    result[i][j] = array.getInt(index);
	                    break;
	                case SHORT:
	                    result[i][j] = array.getShort(index);
	                    break;
	                case BYTE:
	                    result[i][j] = array.getByte(index);
	                    break;
	                default:
	                    result[i][j] = array.getFloat(index); // 尝试float转换
	            }
	        }
	    }
	    return result;
	}

	/**
     * 转换数组为Java对象
     */
    private static Object convertArrayToObject(Array array) {
        if (array.getSize() == 1) {
            return array.getObject(0);
        } else {
            return array.toString();
        }
    }


    /**
     * 获取坐标系统信息
     */
    @SuppressWarnings("deprecation")
	private static Map<String, Object> getCoordinateSystemInfo(NetcdfFile hdfFile) {
        Map<String, Object> coordSystem = new HashMap<>();

        // 查找经纬度变量
        List<String> coordVars = new ArrayList<>();
        for (Variable var : hdfFile.getVariables()) {
            if (isCoordinateVariable(var.getShortName())) {
                coordVars.add(var.getShortName());
            }
        }
        coordSystem.put("coordinateVariables", coordVars);
        // 尝试提取地理范围信息
        try {
            Map<String, Object> bounds = extractGeographicBounds(hdfFile);
            if (!bounds.isEmpty()) {
                coordSystem.put("geographicBounds", bounds);
            }
        } catch (Exception e) {
            log.debug("无法提取地理范围信息", e);
        }

        return coordSystem;
    }

    /**
     * 提取地理范围信息
     */
    private static Map<String, Object> extractGeographicBounds(NetcdfFile hdfFile) {
        Map<String, Object> bounds = new HashMap<>();

        try {
            // 查找经纬度变量
            Variable latVar = hdfFile.findVariable("lat");
            Variable lonVar = hdfFile.findVariable("lon");

            if (latVar != null && lonVar != null) {
                Array latData = latVar.read();
                Array lonData = lonVar.read();

                if (latData.getSize() > 0 && lonData.getSize() > 0) {
                    bounds.put("latitude", new double[] {
                        latData.getDouble(0),
                        latData.getDouble((int) (latData.getSize() - 1))
                    });

                    bounds.put("longitude", new double[] {
                        lonData.getDouble(0),
                        lonData.getDouble((int) (lonData.getSize() - 1))
                    });

                    bounds.put("resolution", Math.abs(latData.getDouble(1) - latData.getDouble(0)));
                }
            }
        } catch (Exception e) {
            // 忽略错误，不是所有HDF文件都有地理坐标
        }

        return bounds;
    }

    /**
     * 获取文件统计信息
     */
    @SuppressWarnings("deprecation")
	private static Map<String, Object> getFileStatistics(NetcdfFile hdfFile) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalVariables", hdfFile.getVariables().size());
        stats.put("totalDimensions", hdfFile.getDimensions().size());
        stats.put("totalAttributes", hdfFile.getGlobalAttributes().size());

        // 计算数据变量和坐标变量的数量
        int dataVars = 0;
        int coordVars = 0;
        for (Variable var : hdfFile.getVariables()) {
            if (isCoordinateVariable(var.getShortName())) {
                coordVars++;
            } else {
                dataVars++;
            }
        }

        stats.put("dataVariables", dataVars);
        stats.put("coordinateVariables", coordVars);

        return stats;
    }

    /**
     * 将JSON写入文件
     */
    private static void writeJsonToFile(String jsonString, String outputPath) throws IOException {
        NcFileUtils.ensureDirectoryExists(outputPath);
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(jsonString);
        }
    }

    /**
     * 快速获取HDF文件摘要信息（不包含详细数据）
     */
    @SuppressWarnings("deprecation")
	public static String getHdfSummary(String hdfFilePath) {
        try (NetcdfFile hdfFile = NetcdfFile.open(hdfFilePath)) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("fileName", FilenameUtils.getBaseName(hdfFile.getLocation()));
            summary.put("fileSize", formatFileSize(new File(hdfFilePath).length()));
            summary.put("variablesCount", hdfFile.getVariables().size());
            summary.put("dimensionsCount", hdfFile.getDimensions().size());

            List<String> variableNames = new ArrayList<>();
            for (Variable var : hdfFile.getVariables()) {
                variableNames.add(var.getShortName());
            }
            summary.put("variableNames", variableNames);
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.error("获取HDF摘要失败: {}", hdfFilePath, e);
            return "{\"error\": \"无法读取HDF文件: " + e.getMessage() + "\"}";
        }
    }

    // 主方法示例
    public static void main(String[] args) {
        String hdfFilePath = "D:\\Download\\BaiduNetdiskDownload\\卫星项目沟通\\L2\\H1E_OPER_OCT_L2B_20251101T030000_20251101T030500_10262_10.h5";
        String outputJsonPath = "D:\\Download\\BaiduNetdiskDownload\\卫星项目沟通\\L2\\H1E_OPER_OCT_L2B_20251101T030000_20251101T030500_10262_10.json";

        // 转换元数据信息
        String result = hdfToJson(hdfFilePath, outputJsonPath, true);
        System.out.println("转换结果: " + result);

        // 获取摘要信息
        String summary = getHdfSummary(hdfFilePath);
        System.out.println("文件摘要: " + summary);

        // 转换为JSON字符串（包含样本数据）
//        String jsonWithData = hdfToJson(hdfFilePath, null, true);
//        System.out.println("包含数据的JSON长度: " + jsonWithData.length());
    }
}
