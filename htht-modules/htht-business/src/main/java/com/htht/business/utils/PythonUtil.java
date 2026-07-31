package com.htht.business.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/15 13:53
 **/
public class PythonUtil {
    public static boolean isPy(String fileName){
        return fileName.endsWith(".py");
    }

    public static String savePyFile(String pyUrl, MultipartFile file){
        File folderfile = new File(pyUrl);
        if(!folderfile.exists()){
            folderfile.mkdirs();
        }
        String filePath= Path.of(pyUrl, Objects.requireNonNull(file.getOriginalFilename())).toString();
        try {
            file.transferTo(new File(filePath));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return filePath;
    }
}
