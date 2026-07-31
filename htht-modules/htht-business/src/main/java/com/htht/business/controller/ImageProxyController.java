package com.htht.business.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.regex.Pattern;
import org.geotools.coverage.grid.GridCoverage2D;


/**
 * 图片代理工具类
 */
@RestController
public class ImageProxyController {

    @Resource
    private RestTemplate restTemplate;

    // 允许反代哥白尼数据空间和USGS Landsat的缩略图域名
    private static final Pattern ALLOWED_DOMAINS = Pattern.compile(
        "^https?://(catalogue\\.dataspace\\.copernicus\\.eu|landsatlook\\.usgs\\.gov)/.*$"
    );
    /**
     * 图片反代接口
     * @param url 目标缩略图URL（前端传递的第三方图片地址）
     */
    @GetMapping("/proxy/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        // 1. 验证URL合法性（必须在白名单内，防止恶意请求）
        if (!ALLOWED_DOMAINS.matcher(url).matches()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        try {
            // 2. 后端请求目标URL，获取图片字节流
            byte[] imageBytes = restTemplate.getForObject(url, byte[].class);

            // 3. 设置响应头（Content-Type自动识别或手动指定）
            HttpHeaders headers = new HttpHeaders();
            // 简单处理：根据URL后缀推测类型（更精确的方式是解析响应的Content-Type）
                headers.setContentType(MediaType.IMAGE_JPEG);
            // 也可以通过请求目标URL的响应头获取真实Content-Type，示例：
            // HttpHeaders targetHeaders = restTemplate.headForHeaders(url);
            // headers.setContentType(targetHeaders.getContentType());

            // 4. 返回图片字节流给前端
            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);

        } catch (RestClientException e) {
            // 处理请求失败（如目标URL不存在、超时等）
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}
