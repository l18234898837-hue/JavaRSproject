package com.htht.algorithm.download.util;

import com.htht.common.exception.ServiceException;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Author: MSFY
 * Description: 这个工具类用于流传输功能，不通过nginx进行下载处理
 * DateTime: 2026/7/20 15:35
 **/
public class DownloadUtil {

    /**
     * 文件流下载
     * @param response 响应对象
     * @param filePath 文件绝对路径
     * @param baseAllowPath 允许访问的根目录（防路径穿越，传null则跳过目录校验）
     * @throws IOException IO异常
     */
    public static void download(HttpServletResponse response, String filePath, String baseAllowPath) throws IOException {
        File file = getFile(filePath, baseAllowPath);

        // 缓存跨域头，修复reset清空跨域问题（之前遗留坑一并修复）
        String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
        String allowMethods = response.getHeader("Access-Control-Allow-Methods");
        String allowHeaders = response.getHeader("Access-Control-Allow-Headers");
        String allowCred = response.getHeader("Access-Control-Allow-Credentials");

        try {
            response.reset();
        } catch (IllegalStateException e) {
            throw new ServiceException("响应流已提交，无法执行下载");
        }

        // reset后回填跨域Header，解决下载接口跨域失效
        if (allowOrigin != null) response.setHeader("Access-Control-Allow-Origin", allowOrigin);
        if (allowMethods != null) response.setHeader("Access-Control-Allow-Methods", allowMethods);
        if (allowHeaders != null) response.setHeader("Access-Control-Allow-Headers", allowHeaders);
        if (allowCred != null) response.setHeader("Access-Control-Allow-Credentials", allowCred);

        // 下载标准响应头
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/octet-stream;charset=UTF-8");
        String filename = file.getName();
        String encodedFileName = URLEncoder.encode(filename, StandardCharsets.UTF_8.name());
        response.addHeader("Content-Disposition",
            String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", encodedFileName, encodedFileName));
        response.setContentLengthLong(file.length());

        // try-with-resources 自动关闭输入流
        try (InputStream inputStream = new FileInputStream(file)) {
            ServletOutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int readLen;
            while ((readLen = inputStream.read(buffer)) > 0) {
                // 增加输出流存活判断，断连后停止写入
                outputStream.write(buffer, 0, readLen);
                outputStream.flush();
            }
        } catch (IOException e) {
            // 判断是否为客户端主动断开连接（两种典型异常信息）
            String errMsg = e.getMessage();
            boolean clientAbort = errMsg != null && (
                errMsg.contains("主机中的软件中止了一个已建立的连接")
                    || errMsg.contains("broken pipe")
                    || errMsg.contains("connection reset")
            );
            if (clientAbort) {
                // 用户主动关闭页面/取消下载，仅打印INFO，不抛异常、不打错误栈
                org.slf4j.LoggerFactory.getLogger(DownloadUtil.class)
                    .info("文件下载被客户端主动中断，文件路径:{}", filePath);
                return;
            }
            // 服务器真实IO故障，抛出业务异常记录错误
            throw new ServiceException("文件下载失败，原始错误：" + errMsg);
        }
    }

    @NotNull
    private static File getFile(String filePath, String baseAllowPath) throws IOException {
        File file = new File(filePath);
        // 1. 校验文件合法性：存在 + 不是文件夹
        if (!file.exists()) {
            throw new ServiceException("系统路径不存在文件：" + filePath);
        }
        if (!file.isFile()) {
            throw new ServiceException("目标路径不是合法文件：" + filePath);
        }

        // 2. baseAllowPath不为null且非空白时，执行路径穿越校验；null/空白直接跳过
        if (baseAllowPath != null && !baseAllowPath.isBlank()) {
            String canonicalBase = new File(baseAllowPath).getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalBase)) {
                throw new ServiceException("禁止访问目录外文件，路径非法：" + filePath);
            }
        }
        return file;
    }

    /**
     * 重载方法：内部调用，自动跳过路径校验（传null）
     */
    public static void download(HttpServletResponse response, String filePath) throws IOException {
        download(response, filePath, null);
    }

}
