package com.htht.web.init;

import com.htcloud.orbit.OrbitHelper;
import com.htcloud.orbit.config.OrekitConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @program: satellite-orbit
 * @description:
 * @author: wzq
 * @date: 2025-07-22
 **/
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(OrekitConfigProperties.class)
public class OrbitInit {

    private final OrekitConfigProperties orekitConfigProperties;

    @PostConstruct
    public void initOrekit() {
        OrbitHelper.init(orekitConfigProperties.getPath(), orekitConfigProperties.getUrl());
    }
}
