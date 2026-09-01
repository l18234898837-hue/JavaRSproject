package com.htht.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisMapperLocationTest {

    @Test
    void scansMapperXmlFromAllClasspathEntries() throws IOException {
        String configuration = new String(
            new ClassPathResource("application.yml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );

        assertTrue(configuration.contains("mapperLocations: classpath*:/mapper/**/*Mapper.xml"));
    }
}
