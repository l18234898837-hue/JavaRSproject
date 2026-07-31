package com.htht.algorithm.data.utils;

import com.htht.algorithm.data.tile.utils.ZipUtil;
import com.htht.common.exception.GlobalException;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/29 16:10
 **/
public class FileStrUtil {
    public static String getExtension(File file){
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        String extension;
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex+1); // 不包含点号
        } else {
            extension = "";
        }
        return extension;
    }

    public static String getOnlineFileExtension(MultipartFile file){
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            return  originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        return "";
    }

    public static String getNameWithoutExtension(File file){
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        String nameWithoutExtension;
        if (lastDotIndex > 0) {
            nameWithoutExtension = fileName.substring(0, lastDotIndex);
        } else {
            nameWithoutExtension = fileName;
        }
        return nameWithoutExtension;
    }

    public static String getDirectory(File file){
        return file.getParent();
    }

    public static File searchFolder(File unZipFile,String folderStr) {
        File[] files = unZipFile.listFiles();
        if (files == null) {
            throw new GlobalException("文件不存在");
        }
        if (files.length == 1) {
            return searchFolder(files[0],folderStr);
        } else {
            for (File file : files) {
                if (file.isDirectory() && file.getName().equals(folderStr)) {
                    return file;
                }
            }
            throw new GlobalException("找不到存储数据的目录");
        }

    }

    public static File searchChildOneFolder(File File,String folderStr){
        File[] files = File.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory() && file.getName().equals(folderStr)) {
                return file;
            }
        }
        return null;
    }

    public static File unZip(File unZipFile,String unZipDir,Boolean delete){
        String extension = getExtension(unZipFile).toLowerCase();
        switch (extension){
            case "zip":
                ZipUtil.unZip(unZipFile, unZipDir,delete);
                break;
            case "tar":
                ZipUtil.unTar(unZipFile, unZipDir,delete);
                break;
            case "gz":
                ZipUtil.unTarGz(unZipFile, unZipDir,delete);
                break;
            default:
                throw new GlobalException("暂不支持该文件类型");
        }
        File directory = new File(unZipDir);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new GlobalException("目录不存在或解压失败");
        }
        return directory;
    }

    /**
     * 递归删除文件夹及其所有内容
     * @param file 要删除的文件或文件夹
     */
    public static void deleteFolderRecursively(File file) {
        deleteFolderRecursively(file,true);
    }
    public static void deleteFolderRecursively(File file ,boolean deleteRoot) {
        if (file.isDirectory()) {
            // 获取文件夹中的所有文件和子文件夹
            File[] files = file.listFiles();
            if (files != null) {
                // 递归删除每个子文件和子文件夹
                for (File subFile : files) {
                    deleteFolderRecursively(subFile);
                }
            }
        }
        if(deleteRoot){
            // 删除文件或空文件夹
            file.delete();
        }
    }



}
