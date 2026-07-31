package com.htht.algorithm.data.utils.dataDownload;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.common.core.domain.entity.SysUser;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.redis.RedisUtils;
import com.htht.system.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DownloadUserService {

    private final SysPermissionService sysPermissionService;

    private static final String DOWNLOADING_USERS_KEY = "data:downloading:user:";
    private static final long EXPIRE_TIME = 12 * 60 * 60; // 12小时

    public void addUserToDownloading() {
        Long userId = LoginHelper.getUserId();
        long timestamp = System.currentTimeMillis();
        String userInfo = userId + ":" + timestamp;

        // 为每个用户单独设置过期时间
        String userKey = DOWNLOADING_USERS_KEY+ userId;
        RedisUtils.setCacheObject(userKey, userInfo, Duration.ofSeconds(EXPIRE_TIME));
    }

    /**
     * 从下载清除用户
     */
    public void removeUserFromDownloading() {
        Long userId = LoginHelper.getUserId();
        String userKey = DOWNLOADING_USERS_KEY + userId;
        // 直接删除用户的key
        RedisUtils.deleteObject(userKey);
    }

    /**
     * 通过userId查询用户是否在下载
     */
    public boolean isUserDownloading() {
        Long userId = LoginHelper.getUserId();
        String userKey = DOWNLOADING_USERS_KEY + userId;
        return RedisUtils.isExistsObject(userKey);
    }

    /**
     * 判断是否有批量下载权限
     */
    public boolean isBatchPre(){
        return isHavePer("batch");
    }

    /**
     * 判断用户是否用于权限
     */
    public boolean isHavePer(String perStr){
        Long userId = LoginHelper.getUserId();
        Set<String> menuPermission = sysPermissionService.getMenuPermission(new SysUser(userId));
        return switch (perStr) {
            case "L1" -> menuPermission.contains("data:download:L1");
            case "L2" -> menuPermission.contains("data:download:L2");
            case "L3" -> menuPermission.contains("data:download:L3");
            case "L4" -> menuPermission.contains("data:download:L4");
            case "L5" -> menuPermission.contains("data:download:L5");
            case "batch" -> menuPermission.contains("data:download:batch");
            case "online" -> menuPermission.contains("data:download:online");
            case "observe" -> menuPermission.contains("data:download:observe");
            default -> false;
        };
    }

    public void checkPer(String perStr){
        switch (perStr) {
            case "L1" -> isCanDownL1();
            case "L2" -> isCanDownL2();
            case "L3" -> isCanDownL3();
            case "L4" -> isCanDownL4();
            case "L5" -> isCanDownL5();
        }
    }

    @SaCheckPermission("data:download:L1")
    public void isCanDownL1(){};
    @SaCheckPermission("data:download:L2")
    public void isCanDownL2(){};
    @SaCheckPermission("data:download:L3")
    public void isCanDownL3(){};
    @SaCheckPermission("data:download:L4")
    public void isCanDownL4(){};
    @SaCheckPermission("data:download:L5")
    public void isCanDownL5(){};

}
