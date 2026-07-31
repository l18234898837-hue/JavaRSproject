package com.htht.algorithm.data.utils.dataDownload.s3Download.impl;

import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.algorithm.data.utils.dataDownload.s3Download.S3Downloader;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/8/7 16:59
 **/
@Service
public class SentinelS3Downloader extends S3Downloader {

    private final StOnlineDownloadInfoService stOnlineDownloadInfoService;

    public SentinelS3Downloader(StOnlineDownloadInfoService stOnlineDownloadInfoService) {
        super();
        this.stOnlineDownloadInfoService = stOnlineDownloadInfoService;
        this.s3Client = S3Client.builder()
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .region(Region.US_EAST_1) // 适用于公开S3存储桶的通用区域
            .build();
    }

    @Override
    public void downloadSuccess(StOnlineDownloadInfo stOnlineDownloadInfo) {
        stOnlineDownloadInfo.setStatus(Short.valueOf("2"));
        stOnlineDownloadInfoService.updateById(stOnlineDownloadInfo);
    }

    @Override
    public void downloadError(StOnlineDownloadInfo stOnlineDownloadInfo,String errorStr) {
        stOnlineDownloadInfo.setStatus(Short.valueOf("3"));
        stOnlineDownloadInfo.setErrorMsg(errorStr);
        stOnlineDownloadInfoService.updateById(stOnlineDownloadInfo);
    }
}
