package com.htht.algorithm.data.utils.dataDownload.s3Download;

import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.thread.annotation.Pooled;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
public abstract class S3Downloader {
    protected S3Client s3Client;


    /**
     * 下载S3存储桶中指定前缀的所有文件
     * @param bucketName S3存储桶名称
     * @param prefix 要下载的路径前缀（如"tiles/50/T/NK/2025/8/4/0/"）
     * @param localDir 本地保存目录
     */
    @Pooled(executor = "OnlineDownloadExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout =12 * 60 * 60 * 1000)
    public void downloadSentinelData(String bucketName, String prefix, String localDir, StOnlineDownloadInfo stOnlineDownloadInfo) {
        File localDirFile = new File(localDir);
        if (!localDirFile.exists()) {
            localDirFile.mkdirs();
            System.out.println("创建本地目录: " + localDir);
        }

        try {
            // 递归获取所有文件并下载
            listAndDownloadObjects(bucketName, prefix, localDir);
            System.out.println("所有文件下载完成！");
            downloadSuccess(stOnlineDownloadInfo);
        } catch (Exception e) {
            System.err.println("下载出错: " + e.getMessage());
            e.printStackTrace();
            downloadError(stOnlineDownloadInfo,e.getMessage());
        } finally {
            s3Client.close(); // 关闭客户端释放资源
        }
    }

    /**
     * 递归列出并下载S3对象
     */
    private void listAndDownloadObjects(String bucketName, String prefix, String localDir) {
        // 构建列表请求
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build();

        ListObjectsV2Response listResponse;
        do {
            listResponse = s3Client.listObjectsV2(listRequest);
            List<S3Object> objects = listResponse.contents();

            // 处理当前页的所有对象
            for (S3Object object : objects) {
                String key = object.key();
                // 跳过目录本身（以/结尾的键）
                if (key.endsWith("/")) {
                    continue;
                }

                // 构建本地文件路径
                String relativePath = key.substring(prefix.length());
                File localFile = new File(localDir, relativePath);

                // 检查文件是否已存在
                if (localFile.exists()) {
                    System.out.println("已存在，跳过: " + localFile.getName());
                    continue;
                }

                // 创建父目录
                if (!localFile.getParentFile().exists()) {
                    localFile.getParentFile().mkdirs();
                }

                // 下载文件
                try {
                    downloadSingleObject(bucketName, key, localFile);
                    System.out.println("下载完成: " + localFile.getName());
                } catch (IOException e) {
                    System.err.println("下载失败 " + key + ": " + e.getMessage());
                }
            }

            // 处理分页（如果有更多结果）
            listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .continuationToken(listResponse.nextContinuationToken())
                .build();

        } while (listResponse.isTruncated());
    }

    /**
     * 下载单个S3对象到本地文件
     */
    private void downloadSingleObject(String bucketName, String key, File localFile) throws IOException {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();

        try (ResponseInputStream inputStream = s3Client.getObject(getRequest);
             FileOutputStream outputStream = new FileOutputStream(localFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    public abstract void downloadSuccess(StOnlineDownloadInfo stOnlineDownloadInfo);
    public abstract void downloadError(StOnlineDownloadInfo stOnlineDownloadInfo,String errorStr);

}
