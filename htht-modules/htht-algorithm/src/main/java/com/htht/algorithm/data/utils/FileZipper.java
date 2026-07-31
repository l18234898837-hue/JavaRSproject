package com.htht.algorithm.data.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//压缩工具包
public class FileZipper {

    /**
     * 文件信息类，包含原文件路径和压缩包中的新文件名
     */
    public static class FileInfo {
        String sourceFilePath;  // 源文件路径
        String zipFileName;     // 压缩包中的文件名

        public FileInfo(String sourceFilePath, String zipFileName) {
            this.sourceFilePath = sourceFilePath;
            this.zipFileName = zipFileName;
        }
    }

    /**
     * 将多个文件压缩到指定的ZIP文件，并在压缩时重命名
     * @param fileInfos 要压缩的文件信息列表
     * @param zipFilePath 压缩包保存路径
     * @throws IOException 处理文件时可能抛出的异常
     */
    public static void zipFiles(List<FileInfo> fileInfos, String zipFilePath) throws IOException {
        // 创建输出流，用于写入ZIP文件
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            byte[] buffer = new byte[1024 * 8];  // 缓冲区

            for (FileInfo fileInfo : fileInfos) {
                File sourceFile = new File(fileInfo.sourceFilePath);

                // 检查源文件是否存在
                if (!sourceFile.exists() || !sourceFile.isFile()) {
                    System.err.println("文件不存在或不是普通文件: " + fileInfo.sourceFilePath);
                    continue;
                }

                // 创建ZIP条目，使用新的文件名
                ZipEntry zipEntry = new ZipEntry(fileInfo.zipFileName);
                zos.putNextEntry(zipEntry);

                // 读取源文件并写入ZIP
                try (FileInputStream fis = new FileInputStream(sourceFile)) {
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }

                zos.closeEntry();  // 关闭当前条目
                System.out.println("已添加文件: " + fileInfo.sourceFilePath + " -> " + fileInfo.zipFileName);
            }

            System.out.println("压缩完成，保存路径: " + zipFilePath);
        }
    }

    public static void main(String[] args) {
        try {
            // 创建要压缩的文件列表及对应的新文件名
            List<FileInfo> fileInfos = new ArrayList<>();
            fileInfos.add(new FileInfo("C:\\Users\\思大锤\\Desktop\\temp\\work\\zipTest\\1\\12345.png", "test.png"));
            fileInfos.add(new FileInfo("C:\\Users\\思大锤\\Desktop\\temp\\work\\zipTest\\2\\12345.tif", "test.tif"));
            fileInfos.add(new FileInfo("C:\\Users\\思大锤\\Desktop\\temp\\work\\zipTest\\3\\12345.txt", "test.txt"));

            // 执行压缩
            zipFiles(fileInfos, "C:\\Users\\思大锤\\Desktop\\temp\\work\\zipTest\\archive.zip");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
