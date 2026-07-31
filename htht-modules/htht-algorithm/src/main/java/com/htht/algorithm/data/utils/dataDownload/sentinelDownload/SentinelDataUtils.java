package com.htht.algorithm.data.utils.dataDownload.sentinelDownload;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.algorithm.data.event.StOnlineDownloadRecordUpdateFilePathEvent;
import com.htht.algorithm.data.event.StOnlineDownloadRecordUpdateProgressEvent;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sentinel卫星数据查询下载工具类（支持分页查询）
 */
public class SentinelDataUtils {
    // API基础配置（不变）
    private static final String TOKEN_URL = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token";
    private static final String CATALOGUE_URL = "https://catalogue.dataspace.copernicus.eu/odata/v1/Products";
    private static final String DOWNLOAD_BASE_URL = "https://download.dataspace.copernicus.eu/odata/v1/Products(%s)/$value";
    private static final String CLIENT_ID = "cdse-public";

    public static final String satelliteType="SENTINEL-2";

    // 其他原有配置（令牌管理、HTTP客户端等保持不变）
    private static String username = "18334261090@163.com";
    private static String password = "Liqian12345.";
    private static String accessToken;
    private static String refreshToken;
    private static long tokenExpireTime;
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 分页查询结果封装类
    @Data
    @ToString
    public static class PaginationResult {
        private List<Product> products;       // 当前页数据
        private int currentPage;              // 当前页码
        private int pageSize;                 // 每页大小
        private long totalCount;              // 总记录数
        private int totalPages;               // 总页数

        // 构造方法
        public PaginationResult(List<Product> products, int currentPage, int pageSize, long totalCount) {
            this.products = products;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.totalCount = totalCount;
            this.totalPages = (int) Math.ceil((double) totalCount / pageSize);
        }

    }

    // ===================== 核心：线程安全的下载任务中断注册表【重中之重】 =====================
    /**
     * key: 下载任务唯一标识 = dataName (你的业务唯一标识，文件名=dataName.zip)
     * value: 该任务的中断标记 (volatile保证多线程可见性，网络请求线程修改后，下载线程立即感知)
     */
    private static final Map<Long, VolatileBoolean> DOWNLOAD_TASK_CANCEL_MAP = new ConcurrentHashMap<>();

    /**
     * 轻量级的volatile布尔封装类，用于多线程间的中断标记传递
     */
    @Setter
    @Getter
    private static class VolatileBoolean {
        private volatile boolean isCancelled = false; // 核心中断标记，volatile必须加
    }

    // ===================== 对外暴露：网络请求调用这个方法，即可打断指定下载任务 =====================
    /**
     * 外部网络请求触发下载中断的核心方法
     * @param id 下载任务的唯一标识（你的业务关键字，和下载时传入的一致）
     * @return true=找到任务并触发中断  false=无此下载任务/任务已完成
     */
    public static void cancelDownloadById(Long id) {
        VolatileBoolean cancelFlag = DOWNLOAD_TASK_CANCEL_MAP.get(id);
        if (cancelFlag != null) {
            cancelFlag.setCancelled(true);
        }
    }



    /**
     * 带分页的Sentinel数据查询方法
     *
     * @param satelliteType 卫星类型(SENTINEL-1/SENTINEL-2)
     * @param platforms     卫星平台列表（如["A", "B", "C"]）
     * @param startDate     开始时间(ISO格式)
     * @param endDate       结束时间(ISO格式)
     * @param pageNum       页码（从1开始）
     * @param pageSize      每页记录数
     * @param onlyOnline    是否只返回在线数据
     * @return 分页结果（包含当前页数据和分页信息）
     */
    public static PaginationResult searchSentinelDataWithPagination(
        String satelliteType, List<String> platforms,
        String startDate, String endDate,
        String polygon,
        int pageNum, int pageSize, boolean onlyOnline) throws IOException {

        // 校验分页参数
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 50; // 限制最大页大小（API通常有上限）

        // 计算跳过的记录数
        int skip = (pageNum - 1) * pageSize;

        // 构建地理范围和筛选条件（与之前相同）

        List<String> filterClauses = new ArrayList<>();
        filterClauses.add(String.format("Collection/Name eq '%s'", satelliteType));
        filterClauses.add(String.format("ContentDate/Start ge '%s'", startDate));
        filterClauses.add(String.format("ContentDate/Start lt '%s'", endDate));
        if(ObjectUtils.isNotNull(polygon)&&StringUtils.isNotBlank(polygon)){
            filterClauses.add(String.format("OData.CSC.Intersects(area=geography'SRID=4326;%s')", polygon));
        }

        // 卫星平台筛选（不变）
        if (platforms != null && !platforms.isEmpty()) {
            List<String> platformClauses = platforms.stream()
                .map(platform -> String.format(
                    "Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'platformSerialIdentifier' and att/OData.CSC.StringAttribute/Value eq '%s')",
                    platform))
                .collect(Collectors.toList());
            filterClauses.add("(" + String.join(" or ", platformClauses) + ")");
        }

        if (onlyOnline) {
            filterClauses.add("Online eq true");
        }
        if ("SENTINEL-2".equals(satelliteType)) {
            filterClauses.add("Attributes/OData.CSC.StringAttribute/any(att:att/Name eq 'instrumentShortName' and att/OData.CSC.StringAttribute/Value eq 'MSI')");
        }

        String filterStr = String.join(" and ", filterClauses);

        // 构建分页查询参数（核心修改：添加$skip和$top）
        HttpUrl.Builder urlBuilder = HttpUrl.parse(CATALOGUE_URL).newBuilder();
        urlBuilder.addQueryParameter("$filter", filterStr);
        urlBuilder.addQueryParameter("$orderby", "ContentDate/Start desc");
        urlBuilder.addQueryParameter("$top", String.valueOf(pageSize));  // 每页记录数
        urlBuilder.addQueryParameter("$skip", String.valueOf(skip));      // 跳过的记录数
        urlBuilder.addQueryParameter("$select", "Id,Name,ContentDate,S3Path,GeoFootprint,Assets,Online");
        urlBuilder.addQueryParameter("$expand", "Assets");
        urlBuilder.addQueryParameter("$count", "true");                   // 请求返回总记录数

        // 发送查询请求（不变）
        String token = getValidToken();
        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer " + token)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("查询数据失败: " + response.code() + ", 筛选条件: " + filterStr);
            }

            String responseBody = response.body().string();
            JsonNode result = objectMapper.readTree(responseBody);

            // 解析总记录数（从@odata.count获取）
            long totalCount = result.has("@odata.count") ? result.get("@odata.count").asLong() : 0;

            // 解析当前页数据
            JsonNode valueNode = result.get("value");
            List<Product> products = new ArrayList<>();
            if (valueNode != null && valueNode.isArray()) {
                for (JsonNode node : valueNode) {
                    Product product = new Product();
                    // 产品信息解析（与之前相同）
                    product.setProductId(node.get("Id").asText());
                    product.setName(node.get("Name").asText());
                    product.setAcquisitionTime(node.get("ContentDate").get("Start").asText());
                    product.setS3Path(node.has("S3Path") ? node.get("S3Path").asText() : "未知");
                    product.setGeoFootprint(node.has("GeoFootprint") ? node.get("GeoFootprint").toString() : "未知");
                    product.setDownloadUrl(String.format(DOWNLOAD_BASE_URL, product.getProductId()));
                    product.setOnline(node.has("Online") && node.get("Online").asBoolean());

                    // 提取缩略图和平台信息（与之前相同）
                    if (node.has("Assets") && node.get("Assets").isArray()) {
                        for (JsonNode asset : node.get("Assets")) {
                            if (asset.has("DownloadLink")) {
                                product.setThumbnailUrl(asset.get("DownloadLink").asText());
                                break;
                            }
                        }
                    }
                    if (node.has("Attributes") && node.get("Attributes").has("OData.CSC.StringAttribute") &&
                        node.get("Attributes").get("OData.CSC.StringAttribute").isArray()) {
                        for (JsonNode attr : node.get("Attributes").get("OData.CSC.StringAttribute")) {
                            if (attr.has("Name") && "platformSerialIdentifier".equals(attr.get("Name").asText()) &&
                                attr.has("Value")) {
                                product.setPlatform(attr.get("Value").asText());
                                break;
                            }
                        }
                    }
                    products.add(product);
                }
            }

            System.out.printf("查询到第%d页数据，共%d条（总记录数：%d）%n",
                pageNum, products.size(), totalCount);
            return new PaginationResult(products, pageNum, pageSize, totalCount);
        }
    }


    // 以下方法保持不变（令牌管理、下载方法、Product实体类等）
    public static void setCredentials(String user, String pwd) {
        username = user;
        password = pwd;
    }

    public static synchronized String getValidToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        if (refreshToken != null) {
            try {
                return refreshAccessToken();
            } catch (Exception e) {
                System.out.println("刷新令牌失败，将重新获取: " + e.getMessage());
            }
        }
        return getNewAccessToken();
    }

    private static String getNewAccessToken() throws IOException {
        FormBody formBody = new FormBody.Builder()
            .add("grant_type", "password")
            .add("client_id", CLIENT_ID)
            .add("username", username)
            .add("password", password)
            .build();

        Request request = new Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取令牌失败: " + response.code());
            }

            JsonNode result = objectMapper.readTree(response.body().string());
            accessToken = result.get("access_token").asText();
            refreshToken = result.get("refresh_token").asText();
            long expireSeconds = result.get("expires_in").asLong();
            tokenExpireTime = System.currentTimeMillis() + (expireSeconds - 60) * 1000;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            System.out.println("成功获取令牌，有效期至: " + sdf.format(new Date(tokenExpireTime)));
            return accessToken;
        }
    }

    private static String refreshAccessToken() throws IOException {
        FormBody formBody = new FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build();

        Request request = new Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("刷新令牌失败: " + response.code());
            }

            JsonNode result = objectMapper.readTree(response.body().string());
            accessToken = result.get("access_token").asText();
            long expireSeconds = result.get("expires_in").asLong();
            tokenExpireTime = System.currentTimeMillis() + (expireSeconds - 60) * 1000;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            System.out.println("成功刷新令牌，有效期至: " + sdf.format(new Date(tokenExpireTime)));
            return accessToken;
        }
    }

    public static boolean downloadProduct(String saveDir, String dataName, String downloadUrl, StOnlineDownloadRecordUpdateProgressEvent event) throws Exception {
        // ========== 步骤1：注册当前下载任务，创建独立的中断标记 ==========
        VolatileBoolean currentCancelFlag = new VolatileBoolean();
        DOWNLOAD_TASK_CANCEL_MAP.put(event.getId(), currentCancelFlag);

        String safeFileName = dataName + ".zip";
        Path saveDirPath = Paths.get(saveDir);
        Path savePath = saveDirPath.resolve(safeFileName);

        // 确保目录存在
        try {
            if (!Files.exists(saveDirPath)) {
                Files.createDirectories(saveDirPath);
                System.out.println("已创建目录: " + saveDirPath.toAbsolutePath());
            } else if (!Files.isDirectory(saveDirPath)) {
                throw new IOException("指定的路径不是目录: " + saveDirPath.toAbsolutePath());
            }

        } catch (IOException e) {
            throw new IOException("无法创建或访问目录: " + saveDirPath.toAbsolutePath() +
                ", 错误原因: " + e.getMessage(), e);
        }



        // ========== 核心：读取本地已下载的文件大小（断点核心） ==========
        long downloadedBytes = 0;
        if (Files.exists(savePath)) {
            downloadedBytes = Files.size(savePath);
            System.out.printf("✅ 检测到断点文件，已下载: %.2f MB%n", downloadedBytes / 1024.0 / 1024.0);
        }
        // 动态选择打开模式
        OpenOption[] openOptions;
        if (Files.exists(savePath)) {
            // 断点续传：文件已存在 → 追加写入
            openOptions = new OpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.APPEND};
        } else {
            // 首次下载：文件不存在 → 创建新文件
            openOptions = new OpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW};
        }

        //发事件传送地址
        StOnlineDownloadRecordUpdateFilePathEvent filePathEvent = new StOnlineDownloadRecordUpdateFilePathEvent(event.getId(),savePath.toString());
        filePathEvent.sendEvent();


        String token = getValidToken();
        // ========== 重点：无需添加任何Range头！服务端不支持，加了也没用 ==========
        Request request = new Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "Bearer " + token)
            .build();

        long totalBytes;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载请求失败: " + response.code() +
                    ", 响应信息: " + response.message());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空，无法下载文件");
            }

            // ========== 核心：总大小直接取服务端返回的真实完整大小，永不累加 ==========
            totalBytes = body.contentLength();
            if (totalBytes <= 0) {
                throw new IOException("无法获取文件真实总大小，下载终止");
            }

            // ========== 优化1：文件已经下载完成，直接返回，避免重复操作 ==========
            if (downloadedBytes >= totalBytes) {
                System.out.println("\n✅ 文件已完整下载，无需重复下载: " + savePath.toAbsolutePath());
                event.sendEvent(100);
                return false;
            }

            System.out.printf("✅ 文件真实总大小: %.2f MB，本次需要下载剩余: %.2f MB%n",
                totalBytes / 1024.0 / 1024.0,
                (totalBytes - downloadedBytes) / 1024.0 / 1024.0);

            // ========== 核心中的核心：纯客户端断点续传的关键代码 ✅✅✅ ==========
            try (InputStream inputStream = body.byteStream();
                 OutputStream outputStream = Files.newOutputStream(savePath,openOptions)) {

                byte[] buffer = new byte[8192];
                long skippedBytes = 0;

                // 步骤1：跳过已下载的字节数（本地已有，不需要再写入）
                if (downloadedBytes > 0) {
                    System.out.println("⏳ 正在跳过已下载的字节数，准备断点续传...");
                    // 高效跳过：使用缓冲流批量跳过，比逐个字节读取快100倍
                    while (skippedBytes < downloadedBytes) {
                        long skip = inputStream.skip(downloadedBytes - skippedBytes);
                        if (skip <= 0) {
                            break; // 跳过完成/流到底了，退出循环
                        }
                        skippedBytes += skip;
                    }
                    System.out.printf("✅ 已跳过 %.2f MB 已下载内容，开始下载剩余部分%n", skippedBytes / 1024.0 / 1024.0);
                }

                // 步骤2：读取剩余字节，追加写入本地文件
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // 1. 每次读取数据前，先检测是否被中断 → 核心打断逻辑
                    if (currentCancelFlag.isCancelled()) {
                        System.out.printf("\n⚠️  【%s】检测到中断指令，优雅终止下载%n", event.getId());
                        return true;
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;

                    // 进度计算精准，断点续传正常显示
                    double progress = (downloadedBytes * 100.0) / totalBytes;
                    event.sendEvent((int) progress);
                    System.out.printf("\r📥 下载进度: %.2f%% (%.2f MB / %.2f MB)",
                        progress,
                        downloadedBytes / 1024.0 / 1024.0,
                        totalBytes / 1024.0 / 1024.0);
                }
            }

            // ========== 最终完整性校验 ==========
            long finalFileSize = Files.size(savePath);
            if (finalFileSize == totalBytes) {
                System.out.println("\n✅ 下载完成！文件完整无损: " + savePath.toAbsolutePath());
                if (event != null) {
                    event.sendEvent(100);
                }
            } else {
                // 保留断点文件！下次可继续下载，绝不删除
                throw new IOException("⚠️ 下载中断，当前文件大小: %.2f MB，预期: %.2f MB，已保留断点文件，重新执行即可继续下载"
                    .formatted(finalFileSize / 1024.0 / 1024.0, totalBytes / 1024.0 / 1024.0));
            }
        }
        return false;
    }
    @Data
    @ToString
    public static class Product {
        private String productId;
        private String name;
        private String acquisitionTime;
        private String s3Path;
        private String geoFootprint;
        private String downloadUrl;
        private String thumbnailUrl;
        private boolean isOnline;
        private String platform;
    }


    public static Map<String,String> periodSearch(
        String satelliteType, List<String> platforms,
        String startDate, String endDate,
        String polygon
    ) throws Exception {
        int pageNum=1;
        int pageSize=10;
        Map<String,String> returnMap=new HashMap<>();

        PaginationResult result = searchSentinelDataWithPagination(
            satelliteType,
            platforms,
            startDate,
            endDate,
            polygon,
            pageNum,
            pageSize,
            true
        );

        result.getProducts().forEach(product -> {
            returnMap.put(product.getName(),product.getDownloadUrl());
        });

        while (++pageNum <= result.getTotalPages()) {
            PaginationResult NextResult = searchSentinelDataWithPagination(
                satelliteType,
                platforms,
                startDate,
                endDate,
                polygon,
                pageNum,
                pageSize,
                true
            );
            NextResult.getProducts().forEach(product -> {
                returnMap.put(product.getName(),product.getDownloadUrl());
            });
        }

        return returnMap;
    }

    // 分页查询示例
    public static void main(String[] args) {
        try {
            // 查询参数
            List<String> platforms = Arrays.asList("A");
            int pageNum = 1;      // 第1页
            int pageSize = 10;    // 每页10条

            // 执行分页查询
            System.out.println(searchSentinelDataWithPagination(
                "SENTINEL-2",
                platforms,
                "2025-09-30",
                "2025-10-09",
                "POLYGON((107.08810765619866 24.37902596715847,107.08810765619866 20.642055996466794,114.76685179823019 22.37902596715847,107.08810765619866 24.37902596715847))",
                pageNum,
                pageSize,
                true
            ));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
