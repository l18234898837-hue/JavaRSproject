package com.htcloud.orbit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "orekit.data")
@Data
@Component
public class OrekitConfigProperties {
    private String path;
    private String url;

}
