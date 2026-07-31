package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.config.PythonProperties;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.dto.*;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.algorithm.data.service.SatelliteCalculateService;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.algorithm.data.utils.FileTreeUtil;
import com.htht.algorithm.algorithm.util.PythonUtil;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.file.FileUtils;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO modeType 0、信噪比敏感性 ,1、L2gen程序，2、SST程序、3、ACL2gen程序
 * DateTime: 2026/5/12 15:56
 **/
@RequiredArgsConstructor
@Service
public class SatelliteCalculateServiceImpl implements SatelliteCalculateService {
    private final TileProperties tileProperties;
    private final PythonProperties pythonProperties;
    private final ISysFileService sysFileService;

    @Override
    public List<SatelliteCalculateFolder> getSatelliteCalculateFolder(String dataFolder,int modeType) {
        List<SatelliteCalculateFolder> rValue=new ArrayList<>();
        if (FileUtils.exist(dataFolder)){
            File folder = new File(dataFolder);
            File[] files = folder.listFiles();
            if(files != null){
                for (File file : files) {
                    SatelliteCalculateFolder satelliteCalculateFolder = new SatelliteCalculateFolder();
                    satelliteCalculateFolder.setName(file.getName());
                    List<SatelliteCalculateData> satelliteCalculateDataList = getSatelliteCalculateData(file,modeType);
                    satelliteCalculateFolder.setData(satelliteCalculateDataList);
                rValue.add(satelliteCalculateFolder);
                }
            }
        }
        return rValue;
    }

    /**
     *
     * @param modeType 0、信噪比敏感性 ,1、L2gen程序
     * @return
     */
    @Override
    public List<SatelliteCalculateFolder> getSatelliteCalculateFolder(int modeType) {
        switch (modeType){
            case 0:
                return getSatelliteCalculateFolder(tileProperties.getSatelliteCalculatePath(),0);
            case 1:
                return getSatelliteCalculateFolder(pythonProperties.getL2genRunPath(),1);
        }
        return List.of();
    }


    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runSNR(AddRunSNR addRunSNR) {
        File file = new File(addRunSNR.getRunFileStr());
        if(!file.exists()){
            throw new GlobalException("文件不存在：" + addRunSNR.getRunFileStr());
        }
        int i = file.getName().lastIndexOf(".");
        String name = file.getName().substring(0, i);
        String parent = file.getParent();
        String outPath = Path.of(parent, name).toString();
        //PythonUtil.run(pythonProperties.getExe2Path(),
//                        pythonProperties.getSnrPath(),
//                true,
//            "-s", "sdgsat1mii",                         // 传感器
//            "-i", addRunSNR.getRunFileStr(),            // 输入文件
//            "-a",
//            String.valueOf(addRunSNR.getArea1()),   // area 4个数字必须连续
//            String.valueOf(addRunSNR.getArea2()),
//            String.valueOf(addRunSNR.getArea3()),
//            String.valueOf(addRunSNR.getArea4()),
//            "-o", outPath
//        );
    }

    @Override
    public FileTreeVO getRunSNRData(String runFileStr) {
        return FileTreeUtil.buildFileTree(Path.of(runFileStr,"SNR").toString());
    }

    @Override
    public String checkRunSensitivity(String runFileStr) {
        File file = new File(runFileStr);
        String snrStr = Path.of( file.getParent(), file.getName().substring(0, file.getName().lastIndexOf(".")), "SNR").toString();
        File snrFolder = new File(snrStr);
        File[] files = snrFolder.listFiles();
        if(files != null){
            for (File file1 : files) {
                if (file1.getName().contains("SNR_sdgsat1mii")) {
                    return file1.getAbsolutePath();
                }
            }
        }
        throw new GlobalException("未找到SNR_sdgsat1mii运行文件，检查或者重新运行算法");
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runSensitivity(String runFileStr, String runSettingFileStr) {
        File file = new File(runFileStr);
        String outPath = Path.of( file.getParent(), file.getName().substring(0, file.getName().lastIndexOf(".")), "Sensitivity").toString();
        //PythonUtil.run(pythonProperties.getExe2Path(),
//            pythonProperties.getSensitivityPath(),
//            true,
//            "-s", "sdgsat1mii",                         // 传感器
//            "-n", runSettingFileStr,            // 输入文件
//            "-o", outPath
//        );
    }

    @Override
    public FileTreeVO getSensitivityData(String runFileStr) {
        return FileTreeUtil.buildFileTree(Path.of(runFileStr,"Sensitivity").toString());
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runL2gen(AddRunL2gen addRunL2gen) {
        System.out.println(addRunL2gen.getRunFileStr());
        File file = new File(addRunL2gen.getRunFileStr());
        String fileNameNotSuffix = file.getName().substring(0, file.getName().lastIndexOf("."));
        // 先拿到绝对路径文件对象
        String outPath = Path.of(pythonProperties.getL2genRunPath(), "data", fileNameNotSuffix + "_L2.h5").toString();

        //PythonUtil.runCommand(pythonProperties.getL2genPath(),
//            pythonProperties.getL2genRunPath(),
//            true,
//            "ifile="+file.getAbsolutePath(),
//            "ofile="+outPath,
//            "l2prod=chlor_a,Rrs_412,Rrs_443,Rrs_490,Rrs_520,Rrs_565,Rrs_670,Rrs_750,latitude,longitude",
//            "aer_opt=-2 "
//            );
    }

    @Override
    public FileTreeVO getSSTData() {
        return FileTreeUtil.buildFileTree(Path.of(pythonProperties.getSstDataPath()).toString());
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runSST(AddRunSST addRunSST) {
        //PythonUtil.runCommand(pythonProperties.getMatlabPath(),pythonProperties.getSstRunPath(),true,
//            "-nodesktop",
//            "-nosplash",
//            "-r",
//            "\"run('AutoProduceSST("+DateUtil.formatDate(addRunSST.getStartDate(),"yyyyMMdd")+","+DateUtil.formatDate(addRunSST.getEndDate(),"yyyyMMdd")+")');exit;\""
//        );

    }

    @Override
    public List<SatelliteCalculateFolder> getACL2gen() {

        SatelliteCalculateFolder h1dCalculateFolder = new SatelliteCalculateFolder();
        h1dCalculateFolder.setName("h1d");
        List<SatelliteCalculateData> h1dData=new ArrayList<>();
        h1dCalculateFolder.setData(h1dData);
        SatelliteCalculateFolder myd03CalculateFolder = new SatelliteCalculateFolder();
        myd03CalculateFolder.setName("myd03");
        List<SatelliteCalculateData> myd03Data=new ArrayList<>();
        myd03CalculateFolder.setData(myd03Data);
        String acL2genRunPath = pythonProperties.getAcL2genRunPath();
        String h1dFolder = Path.of(acL2genRunPath, "h1d").toString();
        String myd03Folder = Path.of(acL2genRunPath, "MYD03").toString();
        File h1dFile = new File(h1dFolder);
        File myd03File = new File(myd03Folder);
        File[] h1dFiles = h1dFile.listFiles();
        if (h1dFiles != null) {
            for (File dFile : h1dFiles) {
                h1dData.add(new SatelliteCalculateData().setName(dFile.getName()).setPath(dFile.getAbsolutePath()));
            }
        }
        File[] myd03Files = myd03File.listFiles();
        if(myd03Files!=null){
            for (File mFile : myd03Files) {
                myd03Data.add(new SatelliteCalculateData().setName(mFile.getName()).setPath(mFile.getAbsolutePath()));
            }
        }
        List<SatelliteCalculateFolder> satelliteCalculateFolders =new ArrayList<>();
        satelliteCalculateFolders.add(h1dCalculateFolder);
        satelliteCalculateFolders.add(myd03CalculateFolder);
        return satelliteCalculateFolders;
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runACL2gen(AddRunACL2gen addRunAcL2gen) {
        System.out.println(pythonProperties.getExePath());
        System.out.println(pythonProperties.getAcL2genPath());
        String geoPath = pythonProperties.getExePath();
        File file = new File(addRunAcL2gen.getRunFileStr());
        String outStr = file.getName().substring(0, file.getName().lastIndexOf(".")) + "_L2.h5";
        String dataFolder = Path.of(pythonProperties.getAcL2genRunPath(), "data",outStr).toString();
        //PythonUtil.run(geoPath,pythonProperties.getAcL2genPath(),true,
//            "-s",
//            "hy1dcocts",
//            "-sr","modisa",
//            "-t",addRunAcL2gen.getRunFileStr(),
//            "-r",addRunAcL2gen.getMod03FileStr(),
//            "-o",dataFolder
//        );

    }

    @Override
    public FileTreeVO getACL2genData() {
        String dataFolder = Path.of(pythonProperties.getAcL2genRunPath(), "data").toString();
        return FileTreeUtil.buildFileTree(dataFolder);
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runSla(AddRunSla addRunSla) {
        SysFile file = sysFileService.getById(addRunSla.getParFileId());
        if(file==null){
            throw new GlobalException("确认是否上传对应文件");
        }
        String geoPath = pythonProperties.getExe2Path();
//        //PythonUtil.runWorkPath(geoPath,pythonProperties.getSlaPath(),Path.of(pythonProperties.getSlaPath()).getParent().getParent().toString(),true,
//            "--start_date", DateUtil.formatDate(addRunSla.getStartDate(),"yyyy-MM-dd"),
//            "--end_date", DateUtil.formatDate(addRunSla.getEndDate(),"yyyy-MM-dd"),
//            "--setting_file",file.getFilePath()
//        );

    }

    @Override
    public FileTreeVO getSlaData() {
        return FileTreeUtil.buildFileTree(Path.of(pythonProperties.getSlaRunPath()).toString());
    }

    @Override
    public FileTreeVO getL2genData() {
        return FileTreeUtil.buildFileTree(Path.of(pythonProperties.getL2genRunPath(),"data").toString());
    }

    @Override
    public List<SatelliteCalculateFolder> getQaInputFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getQaInputPath(),2);
    }

    @Override
    public FileTreeVO getQaOutputData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getQaOutputPath());
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runQaPython1(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(),pythonProperties.getQaPython1Path(),true);
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runQaPython2(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(), pythonProperties.getQaPython2Path(), true);
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runQaPython3(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(), pythonProperties.getQaPython3Path(), true);
    }

    @Override
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void runQaPython4(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(), pythonProperties.getQaPython4Path(), true);
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runQaPython5(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(), pythonProperties.getQaPython5Path(), true);
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runQaPython6(AddRunQaPython addRunQaPython) {
        //PythonUtil.run(pythonProperties.getQaEnvPath(), pythonProperties.getQaPython6Path(), true);
    }
    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runWindSh(AddRunWindSh addRunWindSh) {
        //PythonUtil.runCommand(pythonProperties.getWindShPath(),new File(pythonProperties.getWindShPath()).getParent(),true);

    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runWindPython1(AddRunWindPython addRunWindPython) {
        //PythonUtil.run(pythonProperties.getWindEnvPath(), pythonProperties.getWindPython1Path(), true);
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runWindPython2(AddRunWindPython addRunWindPython) {
        //PythonUtil.run(pythonProperties.getWindEnvPath(), pythonProperties.getWindPython2Path(), true);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputShMulFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputShMulPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputShSy3eFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputShSy3ePath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputShHy2bFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputShHy2bPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputShHy2cFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputShHy2cPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputPython1ccmpFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputPython1ccmpPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputPython1fusionFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputPython1fusionPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputPython1landFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputPython1landPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputPython2FusionFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputPython2FusionPath(),2);
    }

    @Override
    public List<SatelliteCalculateFolder> getWindInputPython2BuoyFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getWindInputPython2BuoyPath(),2);
    }

    @Override
    public FileTreeVO getWindMiddleShFy3eData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getWindMiddleShFy3ePath());
    }

    @Override
    public FileTreeVO getWindMiddleShHy2bData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getWindMiddleShHy2bPath());
    }

    @Override
    public FileTreeVO getWindMiddleShHy2cData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getWindMiddleShHy2CPath());
    }

    @Override
    public FileTreeVO getWindOutputShData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getWindOutputShPath());
    }

    @Override
    public FileTreeVO getWindOutputPython1Data(String runFileStr) {
       return FileTreeUtil.buildFileTree(pythonProperties.getWindOutputPython1Path());
    }

    @Override
    public FileTreeVO getWindOutputPython2Data(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getWindOutputPython2Path());
    }

    @Override
    public List<SatelliteCalculateFolder> getSarwindInputFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getSarwindInputPath(),2);
    }

    @Override
    public FileTreeVO getSarwindOutputData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getSarwindOutputPath());
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runSarwindPython(AddRunSarwindPython addRunSarwindPython) {
        //PythonUtil.run(pythonProperties.getSarwindEnvPath(), pythonProperties.getSarwindPythonPath(), true,"--config","examples/wind_retrieval_request.windows.template.json");
    }

    @Override
    public List<SatelliteCalculateFolder> getSwhInputFolder() {
        return getSatelliteCalculateFolder(pythonProperties.getSwhInputPath(),2);
    }

    @Override
    public FileTreeVO getSwhOutputData(String runFileStr) {
        return FileTreeUtil.buildFileTree(pythonProperties.getSwhOutputPath());
    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runSwhSh(AddRunSwhSh addRunSwhSh) {
        //PythonUtil.runCommand(pythonProperties.getSwhShPath(),new File(pythonProperties.getSwhShPath()).getParent(),true);

    }

    @Pooled(executor = "gridStorageExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    @Override
    public void runSwhPython(AddRunSwhPython addRunSwhPython) {
        //PythonUtil.run(pythonProperties.getSwhEnvPath(), pythonProperties.getSwhPythonPath(), true);
    }


    @NotNull
    private static List<SatelliteCalculateData> getSatelliteCalculateData(File file,int modeType) {
        List<SatelliteCalculateData> satelliteCalculateDataList=new ArrayList<>();
        File[] files1 = file.listFiles();
        if(files1 != null){
            for (File file1 : files1) {
                if(modeType==0){
                    if (file1.getName().contains("tif")&&!file1.getName().contains("enp")) {
                        SatelliteCalculateData satelliteCalculateData = new SatelliteCalculateData();
                        satelliteCalculateData.setName(file1.getName());
                        satelliteCalculateData.setPath(file1.getAbsolutePath());
                        int i = file.getName().lastIndexOf(".");
                        String name = file.getName().substring(0, i);
                        satelliteCalculateData.setThumbPath("/satelliteCalulate/"+ file.getName()+"/"+name+".browse.png");
                        satelliteCalculateDataList.add(satelliteCalculateData);
                    }
                }else if(modeType==1){
                    if(file1.getName().contains(".h5")){
                        SatelliteCalculateData satelliteCalculateData = new SatelliteCalculateData();
                        satelliteCalculateData.setName(file1.getName());
                        satelliteCalculateData.setPath(file1.getAbsolutePath());
                        int i = file1.getName().lastIndexOf(".");
                        String name = file1.getName().substring(0, i);
                        satelliteCalculateData.setThumbPath("/calulate/"+"l2gen"+ file1.getName()+"/"+name+".jpg");
                        satelliteCalculateDataList.add(satelliteCalculateData);
                    }
                }else if(modeType==2){
                    SatelliteCalculateData satelliteCalculateData = new SatelliteCalculateData();
                    satelliteCalculateData.setName(file1.getName());
                    satelliteCalculateData.setPath(file1.getAbsolutePath());
                    int i = file1.getName().lastIndexOf(".");
                    String name = file1.getName().substring(0, i);
                    satelliteCalculateData.setThumbPath("/calulate/"+"l2gen"+ file1.getName()+"/"+name+".jpg");
                    satelliteCalculateDataList.add(satelliteCalculateData);
                }
            }
        }
        return satelliteCalculateDataList;
    }
}
