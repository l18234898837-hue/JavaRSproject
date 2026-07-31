package com.htht.system.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.lang.Assert;

import com.htht.common.constant.CacheConstants;
import com.htht.common.constant.Constants;
import com.htht.common.core.domain.event.LogininforEvent;
import com.htht.common.core.domain.entity.SysUser;
import com.htht.common.core.domain.model.RegisterBody;
import com.htht.common.enums.UserType;
import com.htht.common.exception.user.CaptchaException;
import com.htht.common.exception.user.CaptchaExpireException;
import com.htht.common.exception.user.UserException;
import com.htht.common.utils.MessageUtils;
import com.htht.common.utils.ServletUtils;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.redis.RedisUtils;
import com.htht.common.utils.spring.SpringUtils;
import com.htht.system.domain.SysUserRole;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册校验方法
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysRegisterService {

    private final ISysUserService userService;
    private final ISysConfigService configService;
    
    private final ISysUserRoleService sysUserRoleService;

    /**
     * 注册
     */
    @Transactional
    public void register(RegisterBody registerBody) {
    	Assert.isTrue(registerBody.getRoleId() != null, "用户角色不能为空！");
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        // 校验用户类型是否存在
        String userType = UserType.getUserType(registerBody.getUserType()).getUserType();

        boolean captchaEnabled = configService.selectCaptchaEnabled();
        // 验证码开关
        if (captchaEnabled) {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }
        long userid = System.currentTimeMillis();
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userid);
        sysUser.setDeptId(registerBody.getDeptId());
        sysUser.setUserName(username);
        sysUser.setNickName(registerBody.getNickName());
        sysUser.setPassword(BCrypt.hashpw(password));
        sysUser.setUserType(userType);
        sysUser.setEmail(registerBody.getEmail());
        sysUser.setSex(registerBody.getSex());
        sysUser.setPhonenumber(registerBody.getPhonenumber());
        //用户角色
        SysUserRole roleEntity = new SysUserRole();
        roleEntity.setRoleId(registerBody.getRoleId());
        roleEntity.setUserId(userid);
        sysUserRoleService.save(roleEntity);
        
        if (!userService.checkUserNameUnique(sysUser)) {
            throw new UserException("user.register.save.error", username);
        }
        boolean regFlag = userService.registerUser(sysUser);
        if (!regFlag) {
            throw new UserException("user.register.error");
        }
        recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.defaultString(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        RedisUtils.deleteObject(verifyKey);
        if (captcha == null) {
            recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha)) {
            recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     * @return
     */
    private void recordLogininfor(String username, String status, String message) {
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(status);
        logininforEvent.setMessage(message);
        logininforEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(logininforEvent);
    }

}
