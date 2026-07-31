package com.htht.business.utils.satellite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.htht.business.utils.satellite.domain.ApiRespons;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 接口响应解析工具类
 * 用于解析 {code:0, msg:"", data:[...]} 格式的接口响应
 */
public class ApiResponseParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析API响应为指定类型的对象
     * @param jsonString JSON字符串
     * @param dataType data字段的类型
     * @return ApiResponse对象
     */
    public static <T> ApiRespons<T> parseResponse(String jsonString, Class<T> dataType) throws IOException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(
            ApiRespons.class, dataType
        );
        return objectMapper.readValue(jsonString, type);
    }

    /**
     * 解析API响应为泛型集合类型
     * @param jsonString JSON字符串
     * @param elementType data数组中元素的类型
     * @return ApiResponse对象，data字段为List类型
     */
    public static <T> ApiRespons<List<T>> parseResponseAsList(String jsonString, Class<T> elementType) throws IOException {
        JavaType listType = objectMapper.getTypeFactory().constructParametricType(
            List.class, elementType
        );
        JavaType type = objectMapper.getTypeFactory().constructParametricType(
            ApiRespons.class, listType
        );
        return objectMapper.readValue(jsonString, type);
    }

    /**
     * 解析API响应为Map类型
     * @param jsonString JSON字符串
     * @param keyType Map键的类型
     * @param valueType Map值的类型
     * @return ApiResponse对象，data字段为Map类型
     */
    public static <K, V> ApiRespons<Map<K, V>> parseResponseAsMap(String jsonString,
                                                                   Class<K> keyType,
                                                                   Class<V> valueType) throws IOException {
        JavaType mapType = objectMapper.getTypeFactory().constructParametricType(
            Map.class, keyType, valueType
        );
        JavaType type = objectMapper.getTypeFactory().constructParametricType(
            ApiRespons.class, mapType
        );
        return objectMapper.readValue(jsonString, type);
    }

    /**
     * 解析API响应为JsonNode，适用于不确定data结构的情况
     * @param jsonString JSON字符串
     * @return ApiResponse对象，data字段为JsonNode类型
     */
    public static ApiRespons<JsonNode> parseResponseAsJsonNode(String jsonString) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        ApiRespons<JsonNode> response = new ApiRespons<>();

        if (rootNode.has("code")) {
            response.setCode(rootNode.get("code").asInt());
        }

        if (rootNode.has("msg")) {
            response.setMsg(rootNode.get("msg").asText());
        }

        if (rootNode.has("data")) {
            response.setData(rootNode.get("data"));
        }

        return response;
    }

    /**
     * 解析API响应，只提取data字段为指定类型
     * @param jsonString JSON字符串
     * @param dataType data字段的类型
     * @return data字段的值
     */
    public static <T> T parseDataOnly(String jsonString, Class<T> dataType) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        if (rootNode.has("data")) {
            JsonNode dataNode = rootNode.get("data");
            return objectMapper.treeToValue(dataNode, dataType);
        }
        return null;
    }

    /**
     * 解析API响应，只提取data字段为列表类型
     * @param jsonString JSON字符串
     * @param elementType data数组中元素的类型
     * @return data字段的列表值
     */
    public static <T> List<T> parseDataAsList(String jsonString, Class<T> elementType) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        if (rootNode.has("data")) {
            JsonNode dataNode = rootNode.get("data");
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(
                List.class, elementType
            );
            return objectMapper.readValue(dataNode.traverse(), listType);
        }
        return null;
    }

    /**
     * 检查API响应是否成功
     * @param jsonString JSON字符串
     * @return 如果code为0则返回true，否则返回false
     */
    public static boolean isSuccess(String jsonString) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        return rootNode.has("code") && rootNode.get("code").asInt() == 0;
    }

    /**
     * 获取API响应的错误消息
     * @param jsonString JSON字符串
     * @return 错误消息，如果不存在则返回空字符串
     */
    public static String getErrorMessage(String jsonString) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        if (rootNode.has("msg")) {
            return rootNode.get("msg").asText();
        }
        return "";
    }

    /**
     * 获取API响应的状态码
     * @param jsonString JSON字符串
     * @return 状态码，如果不存在则返回-1
     */
    public static int getStatusCode(String jsonString) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonString);
        if (rootNode.has("code")) {
            return rootNode.get("code").asInt();
        }
        return -1;
    }

    /**
     * 将对象转换为JSON字符串
     * @param obj 要转换的对象
     * @return JSON字符串
     */
    public static String toJsonString(Object obj) throws IOException {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * 创建成功的API响应对象
     * @param data 响应数据
     * @return ApiResponse对象
     */
    public static <T> ApiRespons<T> createSuccessResponse(T data) {
        return new ApiRespons<>(0, "success", data);
    }

    /**
     * 创建失败的API响应对象
     * @param code 错误码
     * @param message 错误消息
     * @return ApiResponse对象
     */
    public static <T> ApiRespons<T> createErrorResponse(int code, String message) {
        return new ApiRespons<>(code, message, null);
    }
}
