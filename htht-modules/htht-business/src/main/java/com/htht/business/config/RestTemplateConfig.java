package com.htht.business.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 设置超时时间（避免请求第三方资源超时阻塞）
        factory.setConnectTimeout(5000); // 连接超时5秒
        factory.setReadTimeout(5000);    // 读取超时5秒
        return new RestTemplate(factory);
    }
}
