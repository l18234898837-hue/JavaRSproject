package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.dto.*;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.algorithm.download.domain.vo.FileTreeVO;

import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/12 15:55
 **/
public interface SatelliteCalculateService {
    List<SatelliteCalculateFolder> getSatelliteCalculateFolder(String dataFolder,int modeType);

    List<SatelliteCalculateFolder> getSatelliteCalculateFolder(int modeType);


    void runSNR(AddRunSNR addRunSNR);

    FileTreeVO getRunSNRData(String runFileStr);

    String checkRunSensitivity(String runFileStr);

    void runSensitivity(String runFileStr, String runSettingFileStr);

    FileTreeVO getSensitivityData(String runFileStr);



    void runL2gen(AddRunL2gen addRunL2gen);

    FileTreeVO getSSTData();

    void runSST(AddRunSST addRunSST);

    List<SatelliteCalculateFolder> getACL2gen();

    void runACL2gen(AddRunACL2gen addRunAcL2gen);

    FileTreeVO getACL2genData();

    void runSla(AddRunSla addRunSla);

    FileTreeVO getSlaData();

    FileTreeVO getL2genData();


    // ===================== QA 模块 =====================
    List<SatelliteCalculateFolder> getQaInputFolder();

    FileTreeVO getQaOutputData(String runFileStr);

    void runQaPython1(AddRunQaPython addRunQaPython);
    void runQaPython2(AddRunQaPython addRunQaPython);
    void runQaPython3(AddRunQaPython addRunQaPython);
    void runQaPython4(AddRunQaPython addRunQaPython);
    void runQaPython5(AddRunQaPython addRunQaPython);
    void runQaPython6(AddRunQaPython addRunQaPython);

    // ===================== Wind 风场模块 =====================
    // 执行脚本
    void runWindSh(AddRunWindSh addRunWindSh);
    void runWindPython1(AddRunWindPython addRunWindPython);
    void runWindPython2(AddRunWindPython addRunWindPython);

    // Input 目录文件夹
    List<SatelliteCalculateFolder> getWindInputShMulFolder();
    List<SatelliteCalculateFolder> getWindInputShSy3eFolder();
    List<SatelliteCalculateFolder> getWindInputShHy2bFolder();
    List<SatelliteCalculateFolder> getWindInputShHy2cFolder();
    List<SatelliteCalculateFolder> getWindInputPython1ccmpFolder();
    List<SatelliteCalculateFolder> getWindInputPython1fusionFolder();
    List<SatelliteCalculateFolder> getWindInputPython1landFolder();
    List<SatelliteCalculateFolder> getWindInputPython2FusionFolder();
    List<SatelliteCalculateFolder> getWindInputPython2BuoyFolder();

    // 中间结果
    FileTreeVO getWindMiddleShFy3eData(String runFileStr);
    FileTreeVO getWindMiddleShHy2bData(String runFileStr);
    FileTreeVO getWindMiddleShHy2cData(String runFileStr);

    // 输出结果
    FileTreeVO getWindOutputShData(String runFileStr);
    FileTreeVO getWindOutputPython1Data(String runFileStr);
    FileTreeVO getWindOutputPython2Data(String runFileStr);

    // ===================== Sarwind SAR风场模块 =====================
    List<SatelliteCalculateFolder> getSarwindInputFolder();
    FileTreeVO getSarwindOutputData(String runFileStr);
    void runSarwindPython(AddRunSarwindPython addRunSarwindPython);

    // ===================== SWH 有效波高模块 =====================
    List<SatelliteCalculateFolder> getSwhInputFolder();
    FileTreeVO getSwhOutputData(String runFileStr);
    void runSwhSh(AddRunSwhSh addRunSwhSh);
    void runSwhPython(AddRunSwhPython addRunSwhPython);
}
