package com.htht.algorithm.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置类
 *
 * @auth wjx
 * @date 2023-4-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 文件上传路径
     */
    private String uploadPath;

}
