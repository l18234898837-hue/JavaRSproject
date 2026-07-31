package com.htht.framework.config;

import com.htht.common.utils.StringUtils;
import com.htht.framework.config.properties.SpringDocProperties;
import com.htht.framework.handler.OpenApiHandler;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springdoc.core.*;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Swagger 文档配置
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Configuration
@AutoConfigureBefore(SpringDocConfiguration.class)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SpringDocConfig {

	private final ServerProperties serverProperties;

	private static final String SCHEME = "Bearer";
	private static final String BEARER_FORMAT = "JWT";

	private static final String tokenUrl = "http://localhost:8080/login";

	@Bean
	@ConditionalOnMissingBean(OpenAPI.class)
	public OpenAPI openApi(SpringDocProperties properties) {
		OpenAPI openApi = new OpenAPI();
		// 文档基本信息
		SpringDocProperties.InfoProperties infoProperties = properties.getInfo();
		Info info = convertInfo(infoProperties);
		openApi.info(info);
		// 扩展文档信息
		openApi.externalDocs(properties.getExternalDocs());
		openApi.tags(properties.getTags());
		openApi.paths(properties.getPaths());
		openApi.components(properties.getComponents());

		// 添加全局安全要求
//		openApi.addSecurityItem(new SecurityRequirement().addList(HttpHeaders.AUTHORIZATION));
		// 定义安全方案
//		openApi.components(new Components()
//				.addSecuritySchemes(HttpHeaders.AUTHORIZATION,
//				new SecurityScheme()
//				.type(SecurityScheme.Type.OAUTH2)
//				.name(HttpHeaders.AUTHORIZATION)
//						.flows(new OAuthFlows()
//								.password(new OAuthFlow()
//										.tokenUrl(tokenUrl)
//										.refreshUrl(tokenUrl)))
//						.type(SecurityScheme.Type.HTTP)
//						.in(SecurityScheme.In.HEADER)
//						.scheme(SCHEME)
//						.bearerFormat(BEARER_FORMAT)));

		Set<String> keySet = properties.getComponents().getSecuritySchemes().keySet();
		List<SecurityRequirement> list = new ArrayList<>();
		SecurityRequirement securityRequirement = new SecurityRequirement();
		keySet.forEach(securityRequirement::addList);
		list.add(securityRequirement);
		openApi.security(list);

		return openApi;
	}

	@Bean
	public GroupedOpenApi publicApi() {
		return GroupedOpenApi.builder()
				.group("default")
				.packagesToScan("com.htht.business.controller") // 确保扫描到Controller包
//				.addOpenApiCustomiser(tagsSortingCustomiser())
				.build();
	}

	private Info convertInfo(SpringDocProperties.InfoProperties infoProperties) {
		Info info = new Info();
		info.setTitle(infoProperties.getTitle());
		info.setDescription(infoProperties.getDescription());
		info.setContact(infoProperties.getContact());
		info.setLicense(infoProperties.getLicense());
		info.setVersion(infoProperties.getVersion());
		return info;
	}

	/**
	 * 自定义 openapi 处理器
	 */
	@Bean
	public OpenAPIService openApiBuilder(Optional<OpenAPI> openAPI, SecurityService securityParser,
			SpringDocConfigProperties springDocConfigProperties, PropertyResolverUtils propertyResolverUtils,
			Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomisers,
			Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomisers,
			Optional<JavadocProvider> javadocProvider) {
		return new OpenApiHandler(openAPI, securityParser, springDocConfigProperties, propertyResolverUtils,
				openApiBuilderCustomisers, serverBaseUrlCustomisers, javadocProvider);
	}

	/**
	 * 对已经生成好的 OpenApi 进行自定义操作
	 */
	@Bean
	public OpenApiCustomiser openApiCustomiser() {
		String contextPath = serverProperties.getServlet().getContextPath();
		String finalContextPath;
		if (StringUtils.isBlank(contextPath) || "/".equals(contextPath)) {
			finalContextPath = "";
		} else {
			finalContextPath = contextPath;
		}
		// 对所有路径增加前置上下文路径
		return openApi -> {
			Paths oldPaths = openApi.getPaths();
			if (oldPaths instanceof PlusPaths) {
				return;
			}
			PlusPaths newPaths = new PlusPaths();
			oldPaths.forEach((k, v) -> newPaths.addPathItem(finalContextPath + k, v));
			openApi.setPaths(newPaths);
		};
	}
	
	 /**
	  * 对标签按名称进行排序
	  * @return
	  */
//	@Bean
//	public OpenApiCustomiser tagsSortingCustomiser() {
//		log.info("标签排序bean被调用");
//	    return openApi -> {
//	        List<Tag> tags = openApi.getTags();
//	        if (tags != null && !tags.isEmpty()) {
//	            // 创建新列表并排序
//	            List<Tag> sortedTags = new ArrayList<>(tags);
//	            sortedTags.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));
//	            openApi.setTags(sortedTags);
//	        }
//	    };
//	}
	/**
	 * 单独使用一个类便于判断 解决springdoc路径拼接重复问题
	 *
	 * @author Lion Li
	 */
	static class PlusPaths extends Paths {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public PlusPaths() {
			super();
		}
	}

}
