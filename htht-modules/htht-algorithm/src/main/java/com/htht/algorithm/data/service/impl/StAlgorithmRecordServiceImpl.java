package com.htht.algorithm.data.service.impl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import com.htht.algorithm.data.config.PythonProperties;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StAlgorithmParamInfo;
import com.htht.algorithm.data.domain.StAlgorithmRecord;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.domain.vo.SubmitPublicVo;
import com.htht.algorithm.data.domain.vo.SubmitUserVo;
import com.htht.algorithm.data.mapper.StAlgorithmRecordMapper;
import com.htht.algorithm.data.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.algorithm.util.PythonUtil;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.algorithm.file.domain.BusFileInfo;
import com.htht.algorithm.file.service.IBusFileInfoService;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.common.core.service.UserService;
import com.htht.common.exception.GlobalException;
import com.htht.common.helper.LoginHelper;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import com.htht.system.util.recordUserOperateUtil;
import jodd.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 算法运行记录表，用于追踪每次算法执行的状态、用户、日志及运行目录 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-18
 */
@Service
@RequiredArgsConstructor
public class StAlgorithmRecordServiceImpl extends ServiceImpl<StAlgorithmRecordMapper, StAlgorithmRecord> implements StAlgorithmRecordService {
    private final TileProperties tileProperties;
    private final IBusDataInfoService busDataInfoService;
    private final IBusFileInfoService busFileInfoService;
    private final StAlgorithmRecordCatalogInfoService stAlgorithmRecordCatalogInfoService;

    private final LandsatScanStore landsatScanStore;
    private final Sentinel2ScanStore sentinel2ScanStore;

    private final PythonProperties pythonProperties;
    private final StCalculateCountService stCalculateCountService;

    private final UserService userService;

    private final ISysFileService sysFileService;
    private final StAlgorithmParamInfoService stAlgorithmParamInfoService;

    @Override
    public void canStart() {
        List<StAlgorithmRecord> list = this.lambdaQuery()
            .eq(StAlgorithmRecord::getUserId, LoginHelper.getUserId())
            .eq(StAlgorithmRecord::getStatus, 1)
            .or()
            .eq(StAlgorithmRecord::getUserId, LoginHelper.getUserId())
            .eq(StAlgorithmRecord::getStatus, 2).list();
        if (!list.isEmpty()) {
            throw new GlobalException("已有任务在排队或者运行，无法进行此操作");
        }
    }

    @Override
    public void canStart(Long userId) {
        List<StAlgorithmRecord> list = this.lambdaQuery()
            .eq(StAlgorithmRecord::getUserId, userId)
            .in(StAlgorithmRecord::getStatus, 0, 1, 2)
            .list();
        if (!list.isEmpty()) {
            throw new GlobalException("已有任务在排队或者运行，无法进行此操作");
        }
    }


    @Override
    public StAlgorithmRecord getUserResult(Long userId) {
        return this.lambdaQuery()
            .eq(StAlgorithmRecord::getUserId, userId)
            .orderByDesc(StAlgorithmRecord::getCreateTime)
            .last("limit 1")
            .one();
    }


    @SaCheckPermission("data:calculate:LandsatAndSentinel2")
    @Override
    public void addSetting(MultipartFile file) {
        canStart();
        try {
            checkFolder(Path.of(getUserFolder()));
            String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
            file.transferTo(Path.of(getUserFolder(), originalFilename));
            recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感计算", "保存", "", "成功");
        } catch (IOException e) {
            e.printStackTrace();
            recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感计算", "保存", "保存配置文件失败", "失败");
            throw new GlobalException("保存配置文件失败");
        }
    }


    @SaCheckPermission("data:calculate:LandsatAndSentinel2")
    @Override
    public void add(AddAlgorithmTaskVo aATV) {
        add(LoginHelper.getUserId(),aATV);
    }

    @SaCheckPermission("data:calculate:LandsatAndSentinel2")
    @Override
    public void add(Long userId, AddAlgorithmTaskVo aATV) {
        canStart(userId);
        checkCount(aATV.getDataIds().length, userId);
        try {
            StAlgorithmRecord record = new StAlgorithmRecord();
            record.setStatus(Short.valueOf("0"));
            record.setUserId(userId);
            record.setQueueOrder(this.baseMapper.getMaxQueueOrder()+1);
            save(record);
            record.setRunFolder(getFolderByAlgorithmId(record.getId()));
            mvFile(aATV,record);
            updateById(record);
            recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感计算","计算","","成功",userService.selectUserNameById(userId));
        } catch (IOException e) {
            e.printStackTrace();
            recordUserOperateUtil.recordUserOperateLogin("遥感应用-遥感计算","计算","保存数据集到运行文件夹失败","失败",userService.selectUserNameById(userId));
            throw new GlobalException("保存数据集到运行文件夹失败："+e.getMessage());
        }
    }

    @Override
    @Pooled(executor = "AlgorithmRecordExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout = 60 * 60 * 1000)
    public void mvFile(AddAlgorithmTaskVo aATV, StAlgorithmRecord record) throws IOException {
        Path copyFolder=Path.of(record.getRunFolder(),"L0");
        checkFolder(copyFolder);
        for (Long dataId : aATV.getDataIds()) {
            BusDataInfo dataInfo = busDataInfoService.getDataInfoById(dataId);
            BusFileInfo fileInfo = busFileInfoService.getFileInfoById(dataInfo.getFileId());
            Files.copy(Paths.get(fileInfo.getPath()), Path.of(copyFolder.toString(), fileInfo.getFilename() + "." + fileInfo.getExtension()), StandardCopyOption.REPLACE_EXISTING );
        }
        if (aATV.getSettingDataIds() != null) {
            for (Long settingDataId : aATV.getSettingDataIds()) {
                StAlgorithmParamInfo algorithmParamInfo = stAlgorithmParamInfoService.getById(settingDataId);
                SysFile file = sysFileService.getById(algorithmParamInfo.getFileId());
                Files.copy(Paths.get(file.getFilePath()), Path.of(getUserFolder(), file.getFileName() + "." + file.getExtension()), StandardCopyOption.REPLACE_EXISTING );
            }
        }
        if (aATV.getSpaceDataIds() != null) {
            for (Long spaceDataId : aATV.getSpaceDataIds()) {
                BusDataInfo dataInfo = busDataInfoService.getDataInfoById(spaceDataId);
                BusFileInfo fileInfo = busFileInfoService.getFileInfoById(dataInfo.getFileId());
                Files.copy(Paths.get(fileInfo.getPath()), Path.of(getUserFolder(), fileInfo.getFilename() + "." + fileInfo.getExtension()), StandardCopyOption.REPLACE_EXISTING );
            }
        }
        record.setStatus(Short.valueOf("1"));
        updateById(record);
    }

    @Override
    public void task() {
//        System.out.println("Algorithm Task Timer Start");
        if (isTaskStart()) {
//            System.out.println("有任务正在运行");
            checkTask();
        } else {
//            System.out.println("没有任务运行");
            runTask();
        }

    }

    @Override
    public Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId() {
        StAlgorithmRecord one = this.lambdaQuery()
            .eq(StAlgorithmRecord::getUserId, LoginHelper.getUserId())
            .orderByDesc(StAlgorithmRecord::getCreateTime)
            .last("limit 1")
            .one();
        Short status = one.getStatus();
        if (status.equals(Short.valueOf("1")) || status.equals(Short.valueOf("2"))) {
            throw new GlobalException("计算任务在排队或者运行中，暂时没有结果数据");
        }
        return getVoByAlgorithmId(one.getId());
    }

    @Override
    public Map<String, Map<String, List<BusDataInfo>>> getVoByAlgorithmId(Long algorithmId) {
        return stAlgorithmRecordCatalogInfoService.getVoByAlgorithmId(algorithmId);
    }

    @Override
    public void submitUser(SubmitUserVo suv) {
        stAlgorithmRecordCatalogInfoService.submitUser(suv);

    }

    @Override
    public void submitPublic(SubmitPublicVo spv) {
        stAlgorithmRecordCatalogInfoService.submitPublic(spv);
    }

    @Override
    public void checkCount(int count, Long userId) {
        if (!LoginHelper.isAdmin(userId)) {
            stCalculateCountService.checkCalculate(count, userId);
        }
    }


    /**
     * 检查是否有任务在运行
     */
    public boolean isTaskStart() {
        List<StAlgorithmRecord> list = lambdaQuery().eq(StAlgorithmRecord::getStatus, 2).list();
        return !ObjectUtil.isNull(list) && !list.isEmpty();
    }

    /**
     * 当无任务运行时运行最近可以运行的任务
     */
    private void runTask() {
        StAlgorithmRecord algorithmRecord = lambdaQuery()
            .eq(StAlgorithmRecord::getStatus, 1)
            .orderByAsc(StAlgorithmRecord::getQueueOrder)
            .last("limit 1")
            .one();
        if (algorithmRecord != null) {
            algorithmRecord.setStatus(Short.valueOf("2"));
            //运行算法
            updateById(algorithmRecord);
            sendMessageToPython(algorithmRecord.getRunFolder());
            Long userId = algorithmRecord.getUserId();
            StAlgorithmRecord temp = lambdaQuery()
                .eq(StAlgorithmRecord::getStatus, 3)
                .eq(StAlgorithmRecord::getUserId, userId)
                .orderByDesc(StAlgorithmRecord::getQueueOrder)
                .last("limit 1")
                .one();
            if (temp != null) {
                stAlgorithmRecordCatalogInfoService.delectByAlgorithmId(temp.getId());
            }
        }

    }

    /**
     * 发送信息给python算法程序
     */
    private void sendMessageToPython(String runFolder) {
//        PythonUtil.run(pythonProperties.getExePath(), pythonProperties.getFilePath(), true, runFolder);
    }

    private void checkTask() {
        List<StAlgorithmRecord> list = lambdaQuery().eq(StAlgorithmRecord::getStatus, 2).list();
        for (StAlgorithmRecord record : list) {
            String logFile = checkFolder(record.getRunFolder());
            if (StringUtil.isNotBlank(logFile)) {
                processLog(logFile, record);
            }
        }
    }

    private String checkFolder(String runFolder) {
        File file = new File(runFolder);
        File[] files = file.listFiles();
        if (files == null) {
            return "";
        }
        for (File file1 : files) {
            if (file1.getName().equals("result.log")) {
                return file1.getAbsolutePath();
            }
        }
        return "";
    }

    private void processLog(String logFile, StAlgorithmRecord record) {
        File file = new File(logFile);
        if (!file.exists()) {
            return;
        }
        String status = "";
        try {
            Path logpath = Paths.get(logFile);
            List<String> lines = Files.readAllLines(logpath);
            for (String line : lines) {
                if (line.contains("status")) {
                    String[] split = line.split("=");
                    status = split[1];
                    break;
                }
            }
            String context = Files.readString(logpath);
            if (status.equals("success")) {
                record.setLog(context);
                record.setStatus(Short.valueOf("3"));
                updateById(record);
                storeRunFolder(record.getRunFolder(), record.getId());
                FileStrUtil.deleteFolderRecursively(new File(record.getRunFolder()), false);

            } else if (status.equals("fail")) {
                record.setLog(context);
                record.setStatus(Short.valueOf("4"));
                updateById(record);
                FileStrUtil.deleteFolderRecursively(new File(record.getRunFolder()), false);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void storeRunFolder(String runFolder, Long recordId) {
        landsatScanStore.storeAlgorithmRunFolder(runFolder, recordId);
        sentinel2ScanStore.storeAlgorithmRunFolder(runFolder, recordId);
    }


    private String getUserFolder() {
        String userId = Objects.requireNonNull(LoginHelper.getUserId()).toString();
        return Path.of(tileProperties.getAlgorithmPath(), userId).toString();
    }

    private String getUserFolder(Long userId) {
        return Path.of(tileProperties.getAlgorithmPath(), userId.toString()).toString();
    }

    private String getFolderByAlgorithmId(Long Id) {
        return Path.of(tileProperties.getAlgorithmPath(), Id.toString()).toString();
    }

    private void checkFolder(Path userFolder) throws IOException {
        // 确保目录存在
        if (!Files.exists(userFolder)) {
            Files.createDirectories(userFolder);
        }
    }
}
