package com.htht.algorithm.data.core.service;

import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.file.domain.BusFileInfo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


public interface CommonService {

    /**
     * 资源上传
     *
     * @param file
     * @return
     * @throws IOException
     */
    BusFileInfo upload(MultipartFile file) throws IOException;

    /**
     * 资源入库
     *
     * @param dataInfo
     */
    void storage(BusDataInfo dataInfo);


}
