package com.htht.algorithm.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/15 11:48
 **/
@Data
@Component
@ConfigurationProperties(prefix="python")
public class PythonProperties {
    private String exePath;
    private String exe2Path;

    private String matlabPath;
    private String l2genPath;

    private String filePath;

    private String snrPath;
    private String sensitivityPath;


    private String acL2genPath;
    private String slaPath;


    //存放运行文件位置

    private String l2genRunPath;
    private String acL2genRunPath;
    private String slaRunPath;

    private String sstRunPath;
    private String sstDataPath;

    private String qaEnvPath;
    private String qaPython1Path;
    private String qaPython2Path;
    private String qaPython3Path;
    private String qaPython4Path;
    private String qaPython5Path;
    private String qaPython6Path;
    private String qaInputPath;
    private String qaOutputPath;

    private String windEnvPath;
    private String windShPath;
    private String windPython1Path;
    private String windPython2Path;
    private String windInputShMulPath;
    private String windInputShSy3ePath;
    private String windInputShHy2bPath;
    private String windInputShHy2cPath;
    private String windInputPython1ccmpPath;
    private String windInputPython1fusionPath;
    private String windInputPython1landPath;
    private String windInputPython2FusionPath;
    private String windInputPython2BuoyPath;
    private String windMiddleShFy3ePath;
    private String windMiddleShHy2bPath;
    private String windMiddleShHy2CPath;
    private String windOutputShPath;
    private String windOutputPython1Path;
    private String windOutputPython2Path;

    private String sarwindEnvPath;
    private String sarwindPythonPath;
    private String sarwindInputPath;
    private String sarwindOutputPath;

    private String swhEnvPath;
    private String swhPythonPath;
    private String swhShPath;
    private String swhInputPath;
    private String swhOutputPath;


}
