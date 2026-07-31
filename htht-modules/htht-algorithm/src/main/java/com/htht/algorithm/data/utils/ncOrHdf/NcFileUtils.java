package com.htht.algorithm.data.utils.ncOrHdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.basic.netcdf2.nc.reader.LatLngDataReader;
import com.basic.netcdf2.nc.reader.model.Bound;
import com.basic.netcdf2.nc.reader.model.NcResult;
import com.basic.netcdf2.nc.reader.options.LatLngOptions;
import com.basic.resultcreator.image.color.ColorCreator;
import com.basic.resultcreator.image.color.ColorOptions;
import com.basic.resultcreator.image.color.GradientColorCreator;
import com.basic.resultcreator.image.creator.NormalImageCreator;
import com.google.common.collect.ImmutableList;

import lombok.extern.slf4j.Slf4j;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

@Slf4j
public class NcFileUtils {

    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final List<String> EXCLUDED_VARIABLES = Arrays.asList("time", "lev", "lat", "lon", "number", "expver");

    /**
     * 由于给原方法添加了个参数，做兼容方法
     * @param ncFile
     * @return
     */
    public static float getCoordinateOffset(NetcdfFile ncFile){
        return getCoordinateOffset(ncFile,LON);
    }

    /**
     * 获取经纬度数据的偏移量（网格分辨率）只适合等距网格
     * @param ncFile NetCDF文件对象
     * @return 经纬度偏移量（度）
     */
    public static float getCoordinateOffset(NetcdfFile ncFile,String LonName) {

        try {
            Variable lonVar = ncFile.findVariable(LonName);
            Array dataArray = lonVar.read();
            if (lonVar.getDimensions().size() == 1) {
                Object arrayObject = lonVar.read().copyTo1DJavaArray();
                if(arrayObject instanceof double[]){
                    double[] lonData = (double[]) lonVar.read().copyTo1DJavaArray();
                    return (float) Math.abs(lonData[0] - lonData[1]);
                }
                if(arrayObject instanceof float[]){
                    float[] lonData = (float[]) lonVar.read().copyTo1DJavaArray();
                    return (float) Math.abs(lonData[0] - lonData[1]);
                }
            } else if(lonVar.getDimensions().size() == 2) {
                // 获取维度大小
                ImmutableList<Dimension> dims = lonVar.getDimensions();
                int latSize = 0;
                int lonSize = 0;
                for (Dimension dim : dims) {
                    if(dim.getName().equals("col")) {
                        latSize = dim.getLength();
                    }
                    if(dim.getName().equals("row")) {
                        lonSize = dim.getLength();
                    }
                }
                Array latArray = lonVar.read(new int[]{0, 0}, new int[]{latSize, lonSize});
//				System.out.println(latArray);
                // 手动转换为二维数组
                float[][] lat2D = new float[latSize][lonSize];
                for (int i = 0; i < latSize; i++) {
                    for (int j = 0; j < lonSize; j++) {
                        lat2D[i][j] = latArray.getFloat(i * lonSize + j);
                    }
                    if(i >= 2) { 	//只需前2行数据计算偏移量即可，后面的数据没必要
                        break;
                    }
                }
                String offset = String.format("%.5f", Math.abs(lat2D[1][0] - lat2D[0][0]));
                return Float.valueOf(offset);
            } else {
                throw new Exception("获取经纬度偏移量失败，错误原因：经纬度维度异常");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("获取坐标偏移量失败", e);
        }
        return 0;
    }

    /**
     * 获取NetCDF文件的维度信息
     * @param ncFile NetCDF文件对象
     * @return 维度名称列表，用逗号分隔
     */
    @SuppressWarnings("deprecation")
    public static String getDimensions(NetcdfFile ncFile) {
        StringBuilder dim = new StringBuilder();
        ImmutableList<Dimension> dd = ncFile.getDimensions();
        for (Dimension dimension : dd) {
            dim.append(dimension.getName()).append(",");
        }
        if (dim.length() > 0) {
            dim.setLength(dim.length() - 1); // 移除最后的逗号
        }
        return dim.toString();
    }

    /**
     * 获取NetCDF文件的维度数组
     * @param ncFile NetCDF文件对象
     * @return 维度名称数组
     */
    @SuppressWarnings("deprecation")
    public static String[] getDimensionNames(NetcdfFile ncFile) {
        ImmutableList<Dimension> dd = ncFile.getDimensions();
        List<String> dimensionNames = new ArrayList<>();
        for (Dimension dimension : dd) {
            dimensionNames.add(dimension.getName());
        }
        return dimensionNames.toArray(new String[0]);
    }

    /**
     * 识别NetCDF文件中的数据变量（排除维度变量）
     * @param ncFile NetCDF文件对象
     * @return 变量信息列表，格式为"变量名,长名称"
     */
    @SuppressWarnings("deprecation")
    public static List<String> identifyDataVariables(NetcdfFile ncFile) {
        List<Variable> variables = ncFile.getVariables();
        List<String> candidateVariables = new ArrayList<>();

        for (Variable var : variables) {
            String varName = var.getShortName();
            String longName = "";

            // 获取变量的长名称
            List<Attribute> attributes = var.getAttributes();
            for (Attribute attr : attributes) {
                if ("long_name".equals(attr.getShortName())) {
                    longName = attr.getStringValue();
                    break;
                }
            }
            // 排除已知的维度变量
            if (!EXCLUDED_VARIABLES.contains(varName.toLowerCase())) {
                candidateVariables.add(varName + "," + longName);
            }
        }

        return candidateVariables;
    }
    /**
     * 获取变量属性信息
     */
    @SuppressWarnings("deprecation")
	public static List<Map<String, Object>> getVariableAttributes(NetcdfFile ncFile, String varName) {
        List<Variable> variables = ncFile.getVariables();
        for (Variable var : variables) {
        	if(var.getShortName().equals(varName)) {
        		return getVariableAttributes(var);
        	}
        }
        return null;
    }


    /**
     * 获取变量属性信息
     */
    @SuppressWarnings("deprecation")
	public static List<Map<String, Object>> getVariableAttributes(Variable var) {
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
     * 解析变量信息字符串
     * @param variableInfo 变量信息字符串，格式为"变量名,长名称"
     * @return 包含变量名和长名称的数组 [变量名, 长名称]
     */
    public static String[] parseVariableInfo(String variableInfo) {
        return variableInfo.split(",", 2);
    }

    /**
     * 创建LatLngOptions配置对象
     * @param variableName 变量名
     * @param offset 网格分辨率
     * @param dimensionNames 所有维度名称
     * @return LatLngOptions配置对象
     */
    public static LatLngOptions createLatLngOptions(String variableName, float offset, String[] dimensionNames) {
        // 移除经纬度维度
        List<String> filteredDimensions = new ArrayList<>();
        for (String dim : dimensionNames) {
            if (!dim.equalsIgnoreCase(LAT) && !dim.equalsIgnoreCase(LON)) {
                filteredDimensions.add(dim);
            }
        }
        String[] levelDimensions = filteredDimensions.toArray(new String[0]);

        if (levelDimensions.length > 0) {
            return LatLngOptions.create(variableName, offset)
                    .setLatitude(LAT)
                    .setLongitude(LON)
                    .setDimensionNames(LAT, LON)
                    .setLevelDimensionNames(levelDimensions);
        } else {
            return LatLngOptions.create(variableName, offset)
                    .setLatitude(LAT)
                    .setLongitude(LON)
                    .setDimensionNames(LON, LAT);
        }
    }

    /**
     * 创建LatLngOptions配置对象
     * @param variableName 变量名
     * @param offset 网格分辨率
     * @param dimensionNames 所有维度名称
     * @return LatLngOptions配置对象
     */
    public static LatLngOptions createLatLngOptionsByIndex(String variableName, float offset, ImmutableList<Dimension> dimensionNames) {
        if("sst".equalsIgnoreCase(variableName)) {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionNames(LON, LAT);
        } else if ("zsd".equalsIgnoreCase(variableName)) {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionNames(LON, LAT);
        } else if("nearshoredir".equalsIgnoreCase(variableName)) {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionIndexes(0, 1)
                .setFillName("nan");
        } else if("nearshorespd".equalsIgnoreCase(variableName)){
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionIndexes(0, 1)
                .setFillName("nan");
        }else if("Grid/SWH_g".equalsIgnoreCase(variableName)){
            String latG="Grid/lat_g";
            String lonG="Grid/lon_g";
            return LatLngOptions.create(variableName, offset)
                .setLatitude(latG)
                .setLongitude(lonG)
                .setDimensionIndexes(1,2)
                .setLevelDimensionIndexes(0);
        }
        if (dimensionNames.size() == 3) {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionIndexes(1,2) // 默认经纬度在1,2索引位，如不在需定制化修改
                .setLevelDimensionIndexes(0);
        } else if (dimensionNames.size() == 4) {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionIndexes(2, 3) // 默认经纬度在2,3索引位，如不在需定制化修改
                .setLevelDimensionIndexes(0, 1);
        } else {
            return LatLngOptions.create(variableName, offset)
                .setLatitude(LAT)
                .setLongitude(LON)
                .setDimensionNames(LON, LAT);
        }
    }

    /**
     * 获取变量的地理范围信息
     * @param ncFilePath NetCDF文件路径
     * @param variableName 变量名
     * @param offset 网格分辨率
     * @param dimensionNames 维度名称数组
     * @return 地理范围信息Bound对象
     * @throws Exception
     */
    public static Bound getVariableBounds(String ncFilePath, String variableName,
                                         float offset, String[] dimensionNames) throws Exception {
        LatLngOptions options = createLatLngOptions(variableName, offset, dimensionNames);
        com.basic.netcdf2.nc.reader.LatLngDataReader reader =
            new com.basic.netcdf2.nc.reader.LatLngDataReader(ncFilePath, options);

        if (reader.hasNext()) {
            NcResult result = reader.next();
            return result.getBound();
        }
        return null;
    }

    /**
     * 获取变量的数据矩阵
     * @param ncFilePath NetCDF文件路径
     * @param variableName 变量名
     * @param offset 网格分辨率
     * @param dimensionNames 维度名称数组
     * @return 数据矩阵
     */
    public static float[][] getVariableData(String ncFilePath, String variableName,
                                           float offset, String[] dimensionNames) throws Exception {
        LatLngOptions options = createLatLngOptions(variableName, offset, dimensionNames);
        com.basic.netcdf2.nc.reader.LatLngDataReader reader =
            new com.basic.netcdf2.nc.reader.LatLngDataReader(ncFilePath, options);

        if (reader.hasNext()) {
            NcResult result = reader.next();
            return result.getDatas();
        }
        return null;
    }

    /**
     * 获取变量的完整信息（数据+范围）
     * @param ncFilePath NetCDF文件路径
     * @param variableName 变量名
     * @param offset 网格分辨率
     * @param dimensionNames 维度名称数组
     * @return NcResult对象，包含数据和范围信息
     * @throws Exception
     */
    public static NcResult getVariableResult(String ncFilePath, String variableName,
                                            float offset, String[] dimensionNames) throws Exception {
        LatLngOptions options = createLatLngOptions(variableName, offset, dimensionNames);
        LatLngDataReader reader = new LatLngDataReader(ncFilePath, options);
        if (reader.hasNext()) {
            return reader.next();
        }
        return null;
    }

    public static NcResult getVariableResult2(String ncFilePath, String variableName,
                                             float offset, ImmutableList<Dimension> dimensionNames) throws Exception {
        LatLngOptions options = createLatLngOptionsByIndex(variableName, offset, dimensionNames);
        LatLngDataReader reader = new LatLngDataReader(ncFilePath, options);
        if (reader.hasNext()) {
            return reader.next();
         }

        //输出图片测试
//        ColorCreator normalColorCreator = new GradientColorCreator(ColorOptions.create(new float[]{0f, 5f})
//            .setColorScale(new String[]{
//                "#2966a3",
//                "#358ca9",
//                "#4A9194",
//                "#4D8D7C",
//                "#4CA44C",
//                "#66A335",
//                "#A1873F",
//                "#A16C5B",
//                "#8C3F5B",
//                "#f13e3e",})
//            .setStep(100));
//        while (reader.hasNext()) {
//            NcResult next = reader.next();//读取到的一层的数据结果
//            NormalImageCreator normalImageCreator = new NormalImageCreator(next.getDatas(), normalColorCreator);
//            normalImageCreator.create("C:\\新建文件夹\\" + next.getDataTime() + ".png");
////                Bound bound = next.getBound();//边界数据
//        }
        return null;
    }

    /**
     * 确保输出目录存在
     * @param filePath 文件路径
     * @throws IOException
     */
    public static void ensureDirectoryExists(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            log.debug("已创建目录: {}", parentDir);
        }
    }

    /**
     * 打开NetCDF文件
     * @param ncFilePath NetCDF文件路径
     * @return NetcdfFile对象
     * @throws IOException
     */
    public static NetcdfFile openNetcdfFile(String ncFilePath) throws IOException {
        return NetcdfFiles.open(ncFilePath);
    }

    /**
     * 获取变量的单位信息
     * @param ncFile NetCDF文件对象
     * @param variableName 变量名
     * @return 变量的单位字符串
     */
    public static String getVariableUnit(NetcdfFile ncFile, String variableName) {
        try {
            Variable var = ncFile.findVariable(variableName);
            if (var != null) {
                Attribute unitAttr = var.findAttribute("units");
                if (unitAttr != null) {
                    return unitAttr.getStringValue();
                }
            }
        } catch (Exception e) {
            log.error("获取变量单位失败: {}", variableName, e);
        }
        return "";
    }

    /**
     * 获取变量的填充值（NoData值）
     * @param ncFile NetCDF文件对象
     * @param variableName 变量名
     * @return 填充值，如果未找到则返回Float.NaN
     */
    public static float getFillValue(NetcdfFile ncFile, String variableName) {
        try {
            Variable var = ncFile.findVariable(variableName);
            if (var != null) {
                Attribute fillValueAttr = var.findAttribute("_FillValue");
                if (fillValueAttr != null) {
                    return fillValueAttr.getNumericValue().floatValue();
                }

                // 尝试其他常见的填充值属性名
                fillValueAttr = var.findAttribute("missing_value");
                if (fillValueAttr != null) {
                    return fillValueAttr.getNumericValue().floatValue();
                }
            }
        } catch (Exception e) {
            log.error("获取变量填充值失败: {}", variableName, e);
        }
        return Float.NaN;
    }


    @SuppressWarnings({ "deprecation" })
	public static Map<String, Object> readGlobalAttributes(NetcdfFile ncFile) {
        Map<String, Object> attributes = new HashMap<>();
        try {
            // 读取NC文件的全局属性
            for (ucar.nc2.Attribute attribute : ncFile.getGlobalAttributes()) {
                attributes.put(attribute.getShortName(), attribute.getValues());
            }
            // 读取维度信息
            Map<String, Object> dimensions = new HashMap<>();
            for (ucar.nc2.Dimension dimension : ncFile.getDimensions()) {
                Map<String, Object> dimInfo = new HashMap<>();
                dimInfo.put("length", dimension.getLength());
                dimInfo.put("name", dimension.getShortName());
                dimInfo.put("unlimited", dimension.isUnlimited());
                dimensions.put(dimension.getShortName(), dimInfo);
            }
            attributes.put("dimensions", dimensions);

            // 读取变量信息
            Map<String, Object> variables = new HashMap<>();
            for (ucar.nc2.Variable variable : ncFile.getVariables()) {
                Map<String, Object> varInfo = new HashMap<>();
                varInfo.put("name", variable.getShortName());
                varInfo.put("description", variable.getDescription());
                varInfo.put("units", variable.getUnitsString());
                varInfo.put("shape", variable.getShape());
                varInfo.put("dataType", variable.getDataType().toString());

                // 读取变量属性
                Map<String, Object> varAttributes = new HashMap<>();
                for (ucar.nc2.Attribute attr : variable.getAttributes()) {
                    varAttributes.put(attr.getShortName(), attr.getValues());
                }
                varInfo.put("attributes", varAttributes);

                variables.put(variable.getShortName(), varInfo);
            }
            attributes.put("variables", variables);

        } catch (Exception e) {
            log.warn("读取NC文件属性失败", e);
        }
        return attributes;
    }

    /**
     * 获取属性值（安全处理各种数据类型）
     */
    public static Object getAttributeValue(Attribute attr) {
        try {
            if (attr.getDataType() == DataType.STRING) {
                return attr.getStringValue();
            } else if (attr.getLength() == 1) {
                return attr.getNumericValue();
            } else {
                // 数组属性
                Array array = attr.getValues();
                return array.toString();

            }
        } catch (Exception e) {
            return "无法读取属性值";
        }
    }

}
