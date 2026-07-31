package com.htht.algorithm.data.tile.utils;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.htht.common.utils.file.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.archiver.tar.TarEntry;
import org.codehaus.plexus.archiver.tar.TarInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Enumeration;

/**
 * 文件解压缩工具类
 *
 * @author wjx
 * @since 2023-4-25
 */
@Slf4j
public class ZipUtil {

    /**
     * 解压zip文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unZip(File file, String targetPath, Boolean delete) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file, "UTF-8"); // 也可以尝试 GBK/GB2312
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File entryFile = new File(targetPath, entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(entryFile)) {
                        IOUtils.copy(is, fos);
                        fos.flush();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解压ZIP文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(zipFile);
            deleteFile(delete,file);
        }
    }

    /**
     * 解压tar文件
     *
     * @param file       压缩包文件
     * @param targetPath 目标文件夹
     * @param delete     解压后是否删除原压缩文件
     */
    public static void unTar(File file, String targetPath, Boolean delete) {
        FileInputStream fis = null;
        TarInputStream tis = null;

        try {
            fis = new FileInputStream(file);
            tis = new TarInputStream(fis, 1024 * 2);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            TarEntry entry = null;
            while (true) {
                entry = tis.getNextEntry();
                if (Objects.isNull(entry)) {
                    break;
                }

                File entryFile = new File(targetPath, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // 确保父目录存在
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // 解压文件
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        IOUtils.copy(tis, fos);
                        fos.flush();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压TAR文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(tis);
            deleteFile(delete,file);
        }
    }


    /**
     * 解压bz2文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unBZ2(File file, String targetPath, Boolean delete) {
        FileInputStream fis = null;
        BZip2CompressorInputStream bis = null;

        try {
            fis = new FileInputStream(file);
            bis = new BZip2CompressorInputStream(fis);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            File tempFile = new File(targetPath, FileUtils.getPrefix(file));
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtils.copy(bis, fos);
                fos.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("解压BZ2文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(bis);
            deleteFile(delete,file);
        }
    }

    /**
     * 解压tar.bz2文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unTarBz2(File file, String targetPath, Boolean delete) {
        FileInputStream fis = null;
        BZip2CompressorInputStream bis = null;
        TarInputStream tis = null;

        try {
            fis = new FileInputStream(file);
            bis = new BZip2CompressorInputStream(fis);
            tis = new TarInputStream(bis, 1024 * 2);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            TarEntry entry = null;
            while ((entry = tis.getNextEntry()) != null) {
                File entryFile = new File(targetPath, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // 确保父目录存在
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // 解压文件
                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        IOUtils.copy(tis, fos);
                        fos.flush();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压TAR.BZ2文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(tis);
            deleteFile(delete,file);
        }
    }

    /**
     * 解压tar.gz文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unTarGz(File file, String targetPath, Boolean delete) {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        GZIPInputStream gzipIs = null;
        TarInputStream tis = null;

        try {
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            gzipIs = new GZIPInputStream(bis);
            tis = new TarInputStream(gzipIs, 1024 * 2);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            TarEntry entry = null;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    File entryDir = new File(targetPath, entry.getName());
                    entryDir.mkdirs();
                } else {
                    File entryFile = new File(targetPath, entry.getName());
                    // 确保父目录存在
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // 解压文件
                    try (FileOutputStream os = new FileOutputStream(entryFile)) {
                        IOUtils.copy(tis, os);
                        os.flush();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压TAR.GZ文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(tis);
            IOUtils.closeQuietly(gzipIs);
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(fis);
            deleteFile(delete,file);
        }
    }

    /**
     * 解压gz文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unGz(File file, String targetPath, Boolean delete) {
        FileInputStream fis = null;
        GZIPInputStream gzipIs = null;

        try {
            fis = new FileInputStream(file);
            gzipIs = new GZIPInputStream(fis);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            File tempFile = new File(targetPath, FileUtils.getPrefix(file));
            try (FileOutputStream os = new FileOutputStream(tempFile)) {
                IOUtils.copy(gzipIs, os);
                os.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("解压GZ文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(gzipIs);
            deleteFile(delete,file);
        }
    }

    /**
     * 解压7Z文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void un7z(File file, String targetPath, Boolean delete) {
        SevenZFile szf = null;

        try {
            szf = new SevenZFile(file);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            SevenZArchiveEntry entry = null;
            while ((entry = szf.getNextEntry()) != null) {
                File entryFile = new File(targetPath, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // 确保父目录存在
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // 解压文件
                    try (FileOutputStream os = new FileOutputStream(entryFile)) {
                        int count = 0;
                        byte[] bytes = new byte[1024 * 2];
                        while ((count = szf.read(bytes)) != -1) {
                            os.write(bytes, 0, count);
                        }
                        os.flush();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压7Z文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(szf);
            deleteFile(delete,file);
        }
    }


    /**
     * 解压rar文件
     *
     * @param file
     * @param targetPath
     * @param delete
     */
    public static void unRar(File file, String targetPath, Boolean delete) {
        Archive archive = null;

        try {
            archive = new Archive(file);

            // 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            FileHeader fileHeader = null;
            while ((fileHeader = archive.nextFileHeader()) != null) {
                File entryFile = new File(targetPath, fileHeader.getFileName());

                if (fileHeader.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // 确保父目录存在
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // 解压文件
                    try (FileOutputStream os = new FileOutputStream(entryFile)) {
                        archive.extractFile(fileHeader, os);
                    }
                }
            }
        } catch (RarException | IOException e) {
            throw new RuntimeException("解压RAR文件失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(archive);
            deleteFile(delete,file);
        }
    }

    /**
     * 遍历zip压缩包下的文件及文件夹
     *
     * @param file
     * @return
     */
    public static List<String> listZip(File file) {
        ZipInputStream zis = null;
        List<String> nameList = new ArrayList<>();
        try {
            // 尝试不同的字符编码
            Charset[] charsets = {Charset.forName("UTF-8"), Charset.forName("GBK"), Charset.forName("GB2312")};

            for (Charset charset : charsets) {
                try {
                    zis = new ZipInputStream(new FileInputStream(file), charset);
                    ZipEntry entry = null;
                    while ((entry = zis.getNextEntry()) != null) {
                        nameList.add(entry.getName());
                    }
                    break; // 如果成功，跳出循环
                } catch (Exception e) {
                    IOUtils.closeQuietly(zis);
                    zis = null;
                    nameList.clear();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("读取ZIP文件列表失败: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(zis);
        }
        return nameList;
    }

    /**
     * 遍历tar压缩包下的文件及文件夹
     *
     * @param file
     * @return
     */
    public static List<String> listTar(File file) {
        TarInputStream tis = null;
        List<String> nameList = new ArrayList<>();
        try {
            tis = new TarInputStream(new FileInputStream(file));
            TarEntry entry = null;
            while ((entry = tis.getNextEntry()) != null) {
                nameList.add(entry.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(tis);
        }
        return nameList;
    }

    /**
     * 遍历7z压缩包下的文件及文件夹
     *
     * @param file
     * @return
     */
    public static List<String> list7z(File file) {
        SevenZFile szf = null;
        List<String> nameList = new ArrayList<>();
        try {
            szf = new SevenZFile(file);
            SevenZArchiveEntry entry = null;
            while ((entry = szf.getNextEntry()) != null) {
                nameList.add(entry.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(szf);
        }
        return nameList;
    }


    private static void deleteFile(boolean delete, File file){
        if (delete) {
            if (!file.delete()) {
                log.warn("Failed to delete file: {}", file.getAbsolutePath());
            }
        }
    }

}
