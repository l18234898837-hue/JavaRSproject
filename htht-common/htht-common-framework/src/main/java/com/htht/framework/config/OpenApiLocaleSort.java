package com.htht.framework.config;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springdoc.core.customizers.OpenApiLocaleCustomizer;
import org.springframework.stereotype.Component;

import com.htht.common.utils.StringUtils;

import cn.hutool.core.collection.CollectionUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OpenApiLocaleSort implements OpenApiLocaleCustomizer {

    @Override
    public void customise(OpenAPI openApi, Locale locale) {
    	List<Tag> tags = openApi.getTags();
//        if (tags != null && !tags.isEmpty()) {
//            // 创建新列表并排序
//            List<Tag> sortedTags = new ArrayList<>(tags);
//            sortedTags.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));
//            openApi.setTags(sortedTags);
//        }
    	
    	if (CollectionUtil.isNotEmpty(tags)) {
            tags.sort(new Comparator<Tag>() {
                @Override
                public int compare(Tag o1, Tag o2) {
                	log.error(o1.toString());
                    if (null == o1 || null == o2) {
                        return 0;
                    }
                    String d1 = o1.getDescription();
                    if (StringUtils.isEmpty(d1) || !d1.contains(".")) {
                        return -1;
                    }
                    String d2 = o2.getDescription();
                    if (StringUtils.isEmpty(d2) || !d2.contains(".")) {
                        return -1;
                    }
                    String[] s1 = d1.split("\\.");
                    String[] s2 = d2.split("\\.");
                    try {
                        return Integer.parseInt(s1[0].trim()) > Integer.parseInt(s2[0].trim()) ? 1 : -1;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    return 0;
                }
            });
        }
        
    }
}
