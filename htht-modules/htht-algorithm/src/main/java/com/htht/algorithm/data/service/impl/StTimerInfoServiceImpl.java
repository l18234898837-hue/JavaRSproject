package com.htht.algorithm.data.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.basic.task.annotation.Parser;
import com.basic.task.core.CommonHandler;
import com.basic.task.model.ScheduleJob;
import com.basic.task.model.TaskResult;
import com.basic.task.service.QuartzService;
import com.basic.task.task.impl.base.BaseTask;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.StTimerInfo;
import com.htht.algorithm.data.domain.StTimerLog;
import com.htht.algorithm.data.domain.vo.AddAlgorithmTaskVo;
import com.htht.algorithm.data.mapper.StTimerInfoMapper;
import com.htht.algorithm.data.service.StAlgorithmRecordService;
import com.htht.algorithm.data.service.StTimerInfoService;
import com.htht.algorithm.data.service.StTimerLogService;
import com.htht.algorithm.data.service.StUsgsInfoService;
import com.htht.algorithm.thread.annotation.Pooled;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 定时器信息表，存储定时任务的配置信息 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StTimerInfoServiceImpl extends ServiceImpl<StTimerInfoMapper, StTimerInfo> implements StTimerInfoService {
    public static final String QUARTZ_TASK_GROUP_NAME = "timer-task";
    public static final String QUARTZ_TASK_NAME_ALGORITHM = "计算任务";
    public static final String QUARTZ_TASK_NAME_DOWNLOAD = "下载任务";

    private final QuartzService mQuartzService;
    private final CommonHandler commonHandler;

    private final StUsgsInfoService stUsgsInfoService;
    private final StAlgorithmRecordService stAlgorithmRecordService;

    private final TileProperties tileProperties;
    private final StTimerLogService stTimerLogService;


    //更新方法重写
    @Override
    public boolean updateById(StTimerInfo entity) {
        if(entity.getRunText()!=null){
            checkAdd( entity);
        }
        StTimerInfo entityPre = getById(entity.getId());
        if (entityPre == null)
            return false;
        if (entity.getStatus() == 1) {
            if (entityPre.getStatus() == 0) {
                timerStart(entity); // 状态 0 -> 1，向Quartz中添加任务
            } else if (!entity.getRunTime().equals(entityPre.getRunTime()) // 运行时间发生改变
                || !entity.getRunDate().split("[-—]")[0].equals(entityPre.getRunDate().split("[-—]")[0]) // 或运行日期（起点）发生改变
                || (entity.getType() == 2 && !entity.getFrequency().equals(entityPre.getFrequency()))) { // 或周期任务的频率发生改变，则更新Quartz中任务的cron
                String jobIdentity = (entity.getTaskType() == 2 ? QUARTZ_TASK_NAME_ALGORITHM : QUARTZ_TASK_NAME_DOWNLOAD) + entity.getId();
                ScheduleJob scheduleJob = new ScheduleJob();
                scheduleJob.setJobGroup(jobIdentity);
                scheduleJob.setJobName(jobIdentity);
                scheduleJob.setCronExpression(calculateCronExpression(entity));
                mQuartzService.updateJobCron(scheduleJob);
            }
        } else if (entityPre.getStatus() == 1)
            timerStop(entity); // 状态 1 -> 0，从Quartz中删除任务
        return super.updateById(entity);
    }

    //重写删除代码
    @Override
    public boolean removeById(Serializable id) {
        StTimerInfo stTimerInfo = getById(id);
        if (stTimerInfo != null&&stTimerInfo.getStatus() == 1)
            timerStop(stTimerInfo);
        return super.removeById(id);
    }

    //定时器开启
    public void timerStart(StTimerInfo stTimerInfo) {
        String jobIdentity = (stTimerInfo.getTaskType() == 2 ? QUARTZ_TASK_NAME_ALGORITHM : QUARTZ_TASK_NAME_DOWNLOAD) + stTimerInfo.getId();
        ScheduleJob scheduleJob = new ScheduleJob();
        scheduleJob.setJobGroup(jobIdentity);
        scheduleJob.setJobName(jobIdentity);
        scheduleJob.setAlias(jobIdentity);
        scheduleJob.setDescription(jobIdentity);
        scheduleJob.setTaskInfos(commonHandler.getTasks().get("timer-task").stream()
            .filter(taskInfo -> taskInfo.getTaskName().equals(stTimerInfo.getTaskType() == 2 ? QUARTZ_TASK_NAME_ALGORITHM : QUARTZ_TASK_NAME_DOWNLOAD)).toList());
        scheduleJob.getTaskInfos().get(0).getParamInfoMap().get("id").setValue(stTimerInfo.getId().toString());
        scheduleJob.setCronExpression(calculateCronExpression(stTimerInfo));
        if (mQuartzService.addJob(scheduleJob) == null)
            log.error("Quartz定时任务添加失败");
    }

    //计算时间Cron表达解析
    private String calculateCronExpression(StTimerInfo stTimerInfo) {
        if (stTimerInfo.getType() == 1) { // 将"年月日"和"时:分:秒"格式转换为七段式cron表达式"秒 分 时 日 月 ? 年"
            String[] timeParts = stTimerInfo.getRunTime().split(":");
            if (timeParts.length == 3)
                return String.format("%s %s %s %s %s ? %s",
                    timeParts[2], // 秒
                    timeParts[1], // 分
                    timeParts[0], // 时
                    stTimerInfo.getRunDate().substring(6), // 日
                    stTimerInfo.getRunDate().substring(4, 6), // 月
                    stTimerInfo.getRunDate().substring(0, 4)  // 年
                );
        } else if (stTimerInfo.getType() == 2) {
            StringBuilder cronExpression = new StringBuilder();
            String[] timeParts = stTimerInfo.getRunTime().split(":");
            String startDate = stTimerInfo.getRunDate().split("[-—]")[0];
            String[] dateParts = new String[]{startDate.substring(0, 4), startDate.substring(4, 6), startDate.substring(6)};
            if (timeParts.length == 3) { // 根据frequency设置cron表达式
                switch (stTimerInfo.getFrequency()) {
                    case 3: // 每月
                        cronExpression.append(String.format("%s %s %s %s * ? *", timeParts[2], timeParts[1], timeParts[0], dateParts[2]));
                        break;
                    case 2: // 每周
                        cronExpression.append(String.format("%s %s %s ? * %s *", timeParts[2], timeParts[1], timeParts[0],
                            LocalDate.parse(String.join("-", dateParts)).getDayOfWeek().getValue()));
                        break;
                    case 1: // 每天
                        cronExpression.append(String.format("%s %s %s * * ? *", timeParts[2], timeParts[1], timeParts[0]));
                        break;
                    default:
                        cronExpression.append(String.format("%s %s %s * * ? *", timeParts[2], timeParts[1], timeParts[0]));
                        break;
                }
                return cronExpression.toString();
            }
        }
        return null;
    }

    public void timerStop(StTimerInfo stTimerInfo) {
        String jobIdentity = (stTimerInfo.getTaskType() == 2 ? QUARTZ_TASK_NAME_ALGORITHM : QUARTZ_TASK_NAME_DOWNLOAD) + stTimerInfo.getId();
        ScheduleJob scheduleJob = new ScheduleJob();
        scheduleJob.setJobGroup(jobIdentity);
        scheduleJob.setJobName(jobIdentity);
        mQuartzService.interruptJob(scheduleJob);
        if (!mQuartzService.deleteJob(scheduleJob))
            log.error("Quartz定时任务删除失败");
    }

    @Parser(name = QUARTZ_TASK_NAME_ALGORITHM, group = QUARTZ_TASK_GROUP_NAME, params = {"id:任务ID"})
    public TaskResult<String> algorithmTask(Map<String, Object> map, BaseTask<File> task) {
        StTimerInfo stTimerInfo = getById(Long.parseLong(map.get("id").toString()));
        if (stTimerInfo == null || stTimerInfo.getTaskType() != 2)
            return TaskResult.error("任务不存在");
        // TODO: 执行算法任务
        log.info("开始执行算法任务：{}", stTimerInfo.getId());
        packRunTask(stTimerInfo);
        return TaskResult.success("执行完成", "");
    }

    @Parser(name = QUARTZ_TASK_NAME_DOWNLOAD, group = QUARTZ_TASK_GROUP_NAME, params = {"id:任务ID"})
    public TaskResult<String> dataInfoTask(Map<String, Object> map, BaseTask<File> task) {
        StTimerInfo stTimerInfo = getById(Long.parseLong(map.get("id").toString()));
        if (stTimerInfo == null || stTimerInfo.getTaskType() != 1)
            return TaskResult.error("任务不存在");
        if (stTimerInfo.getType() == 2) {
            String[] runDateComponent = stTimerInfo.getRunDate().split("[-—]");
            LocalDate startDate = LocalDate.parse(String.join("-", runDateComponent[0].substring(0, 4),
                runDateComponent[0].substring(4, 6), runDateComponent[0].substring(6)));
            LocalDate endDate = LocalDate.parse(String.join("-", runDateComponent[1].substring(0, 4),
                runDateComponent[1].substring(4, 6), runDateComponent[1].substring(6)));
            if (LocalDate.now().isBefore(startDate) || LocalDate.now().isAfter(endDate))
                // 这里属于程序逻辑跳过执行，不算执行失败，为了避免触发其他逻辑，此处返回成功
                return TaskResult.success("任务不在可执行时间段", "");
        }
        // TODO: 执行下载任务
        log.info("开始执行下载任务：{}", stTimerInfo.getId());
        packRunTask(stTimerInfo);
        return TaskResult.success("执行完成", "");
    }


    @Override
    public TableDataInfo<StTimerInfo> page(StTimerInfo stTimerInfo, PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        return TableDataInfo.build(lambdaQuery()
            .eq(StringUtils.isNotBlank(stTimerInfo.getRunDate()), StTimerInfo::getRunDate, stTimerInfo.getRunDate())
            .eq(ObjectUtils.isNotNull(stTimerInfo.getTaskType()), StTimerInfo::getTaskType,stTimerInfo.getTaskType())
            .eq(ObjectUtils.isNotNull(stTimerInfo.getType()), StTimerInfo::getType, stTimerInfo.getType())
            .eq(ObjectUtils.isNotNull(stTimerInfo.getStatus()), StTimerInfo::getStatus, stTimerInfo.getStatus())
            .eq(StTimerInfo::getUserId, userId).page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize())));
    }

    @Override
    public void checkAdd(StTimerInfo stTimerInfo) {
        Short taskType = stTimerInfo.getTaskType();
        if(taskType == 1){
            Short type = stTimerInfo.getType();
            JSONObject parse =(JSONObject) JSON.parse(stTimerInfo.getRunText());
            if(type==1){
                Integer onlineType = parse.getInteger("onlineType");
                if(onlineType==1){
                    String dataName = parse.getString("dataName");
                    String datasetId = parse.getString("datasetId");
                    String entityId = parse.getString("entityId");
                    if(dataName==null||datasetId==null||entityId==null){
                        throw new GlobalException("传入参数有问题");
                    }
                    int dataNameLen = dataName.split(",").length;
                    int datasetIdLen = datasetId.split(",").length;
                    int entityIdLen = entityId.split(",").length;
                    if(dataNameLen!=datasetIdLen||datasetIdLen!=entityIdLen){
                        throw new GlobalException("传入参数长度有问题");
                    }
                }else if(onlineType==2){
                    String dataName = parse.getString("dataName");
                    String downloadUrl = parse.getString("downloadUrl");
                    if(dataName==null||downloadUrl==null){
                        throw new GlobalException("传入参数有问题");
                    }
                    int dataNameLen = dataName.split(",").length;
                    int downloadUrlLen = downloadUrl.split(",").length;
                    if(dataNameLen!=downloadUrlLen){
                        throw new GlobalException("传入参数长度有问题");
                    }
                }else{
                    throw new GlobalException("未知在线卫星类型");
                }

            }else if(type==2){
                JSONArray datasetName = parse.getJSONArray("datasetNames");
                JSONArray sentinel2Types = parse.getJSONArray("sentinel2Types");
                String geoJsonStr = parse.getString("geoJsonStr");
                String wtk = parse.getString("wtk");

                if(datasetName==null&&sentinel2Types==null){
                    throw new GlobalException("确定是否选择了卫星类型");
                }
                if(geoJsonStr==null||wtk==null){
                    throw new GlobalException("确定是否选择了空间范围");
                }

            }else{
                throw new GlobalException("未知任务时间类型");
            }
        }else if(taskType ==2){
            JSONObject parse =(JSONObject) JSON.parse(stTimerInfo.getRunText());
            if(stTimerInfo.getType()==1){
                JSONArray dataIds = parse.getJSONArray("dataIds");
                if(dataIds==null){
                    throw new GlobalException("传入参数有问题");
                }
            }else{
                throw new GlobalException("未知任务时间类型");
            }
        }else{
            throw new GlobalException("未知任务类型");
        }
    }
    @Pooled(executor = "OnlineDownloadExecutor", poolOverAct = Pooled.PoolOverAct.NEW_THREAD, async = true, timeout =12 * 60 * 60 * 1000)
    @Override
    public void TestRunTask(StTimerInfo stTimerInfo) {
        Long userId = LoginHelper.getUserId();
        packRunTask(stTimerInfo);
    }

    @Override
    public void packRunTask(StTimerInfo stTimerInfo) {
        LocalDateTime now = LocalDateTime.now();
        StTimerLog stTimerLog=new StTimerLog();
        stTimerLog.setUserId(stTimerInfo.getUserId());
        stTimerLog.setTaskType(stTimerInfo.getTaskType());
        stTimerLog.setTaskName(stTimerInfo.getName());
        stTimerLog.setRunTime(now);
        stTimerLog.setRunStatus((short)3);
        stTimerLogService.save(stTimerLog);
        try {
            runTask(stTimerInfo);
            stTimerLog.setRunStatus((short)1);
            LocalDateTime now2 = LocalDateTime.now();
            stTimerLog.setRunDuration((int) Duration.between(now,now2).getSeconds());
            if(stTimerInfo.getType()==1){
                removeById(stTimerInfo.getId());
            }
        } catch (Exception e) {
            if(StringUtils.isBlank(e.getMessage())){
                stTimerLog.setErrorInfo(e.toString());
            }else{
                stTimerLog.setErrorInfo(e.getMessage());
            }
            stTimerLog.setRunStatus((short)2);
        }finally {
            stTimerLogService.updateById(stTimerLog);
        }
    }

    @Override
    public void runTask(StTimerInfo stTimerInfo) throws  Exception{
        Short taskType = stTimerInfo.getTaskType();
        if(taskType == 1){
            Short type = stTimerInfo.getType();
            JSONObject parse =(JSONObject) JSON.parse(stTimerInfo.getRunText());
            if(type==1){
                Integer onlineType = parse.getInteger("onlineType");
                if(onlineType==1){
                    String dataName = parse.getString("dataName");
                    String datasetId = parse.getString("datasetId");
                    String entityId = parse.getString("entityId");
                    String[] dataNames = dataName.split(",");
                    String[] datasetIds = datasetId.split(",");
                    String[] entityIds = entityId.split(",");
                    for(int i=0;i<dataNames.length;i++){
                        stUsgsInfoService.downloadLandsatData(dataNames[i],datasetIds[i], entityIds[i], stTimerInfo.getUserId(),true);
                    }
                }else if(onlineType==2){
                    String dataName = parse.getString("dataName");
                    String downloadUrl = parse.getString("downloadUrl");
                    String[] dataNames = dataName.split(",");
                    String[] downloadUrls = downloadUrl.split(",");
                    for(int i=0;i<dataNames.length;i++){
                        stUsgsInfoService.downloadSentinelData(dataNames[i],downloadUrls[i],stTimerInfo.getUserId(),true);
                    }
                }else{
                    throw new GlobalException("未知在线卫星类型");
                }

            }else if(type==2){
                JSONArray datasetNames = parse.getJSONArray("datasetNames");
                JSONArray sentinel2Types = parse.getJSONArray("sentinel2Types");
                String geoJsonStr = parse.getString("geoJsonStr");
                String wtk = parse.getString("wtk");
                List<String> datasetNameList = new ArrayList<>();
                for (int i = 0; i < datasetNames.size(); i++) {
                    datasetNameList.add(datasetNames.getString(i));
                }
                List<String> sentinel2TypeList = new ArrayList<>();
                for (int i = 0; i < sentinel2Types.size(); i++) {
                    sentinel2TypeList.add(sentinel2Types.getString(i));
                }
                String lastDate = parse.getString("lastDate");
                if(lastDate==null){
                    lastDate=tileProperties.getOnlineDate();
                }
                try {
                    stUsgsInfoService.downloadAreaLoop(datasetNameList,sentinel2TypeList,geoJsonStr,wtk,lastDate,stTimerInfo.getUserId());
                    parse.put("lastDate",lastDate);
                    stTimerInfo.setRunText(parse.toJSONString());
                    updateById(stTimerInfo);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }else{
                throw new GlobalException("未知任务时间类型");
            }
        }else if(taskType ==2){
            JSONObject parse =(JSONObject) JSON.parse(stTimerInfo.getRunText());
            if(stTimerInfo.getType()==1){
                AddAlgorithmTaskVo addAlgorithmTaskVo = new AddAlgorithmTaskVo();
                JSONArray dataIds = parse.getJSONArray("dataIds");
                Long[] dataIdsArray = new Long[dataIds.size()];
                for (int i = 0; i < dataIds.size(); i++) {
                    dataIdsArray[i] = dataIds.getLong(i);
                }
                addAlgorithmTaskVo.setDataIds(dataIdsArray);
                JSONArray spaceDataIds = parse.getJSONArray("spaceDataIds");
                if(spaceDataIds!=null&& !spaceDataIds.isEmpty()){
                    Long[] spaceDataIdsArray = new Long[spaceDataIds.size()];
                    for (int i = 0; i < spaceDataIds.size(); i++) {
                        spaceDataIdsArray[i] = spaceDataIds.getLong(i);
                    }
                    addAlgorithmTaskVo.setSpaceDataIds(spaceDataIdsArray);
                }
                JSONArray settingDataIds = parse.getJSONArray("settingDataIds");
                if(settingDataIds!=null&& !settingDataIds.isEmpty()){
                    Long[] settingDataIdsArray = new Long[settingDataIds.size()];
                    for (int i = 0; i < settingDataIds.size(); i++) {
                        settingDataIdsArray[i] = settingDataIds.getLong(i);
                    }
                    addAlgorithmTaskVo.setSettingDataIds(settingDataIdsArray);
                }
                stAlgorithmRecordService.add(stTimerInfo.getUserId(),addAlgorithmTaskVo);
            }else{
                throw new GlobalException("未知任务时间类型");
            }
        }else{
            throw new GlobalException("未知任务类型");
        }
    }


}
