package com.htht.business.user.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.util.RandomUtil;
import com.htht.business.user.domain.EmailResetPasswordBody;
import com.htht.common.core.domain.R;
import com.htht.common.core.domain.entity.SysUser;
import com.htht.common.exception.user.CaptchaException;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.email.MailUtils;
import com.htht.common.utils.redis.RedisUtils;
import com.htht.framework.config.properties.MailProperties;
import com.htht.system.service.SysLoginService;
import com.htht.system.service.impl.SysUserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.htht.business.user.constants.Constants;
import com.htht.business.user.constants.CacheConstants;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.time.Duration;

/**
 * TODO 通过邮件进入链接重置密码方法
 *
 * @author msfy
 */

@SaIgnore
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emailResetPassword")
public class EmailResetPasswordController {



    private final MailProperties mailProperties;
    private final SysLoginService sysLoginService;
    private final SysUserServiceImpl userService;

    /**
     * 发送邮件，让其可以跳转到对应内容
     *
     * @param email 邮箱
     */
    @GetMapping("/send")
    public R<Void> sendEmailCode(@NotBlank(message = "{user.email.not.blank}") String email) {
        if (!mailProperties.getEnabled()) {
            return R.fail("当前系统没有开启邮箱功能！");
        }
        String key = CacheConstants.EMAIL_RESET_PASSWORD_KEY + email;
        String code = RandomUtil.randomNumbers(4);
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.EMAIL_RESET_PASSWORD_EXPIRATION));

//        String htmlUrl=Constants.WEBSITE_URL+Constants.EMAIL_REDIRECT_URL+"?code="+code+"&email="+email;

        String htmlContent=
            "<html><body>"
            + "<h1>欢迎访问我们的网站,验证码有效期为" + Constants.EMAIL_RESET_PASSWORD_EXPIRATION +"分钟，请尽快填写。</h1>"
//            + "<a href='"+htmlUrl+"'>"+htmlUrl+"</a></p>"
            + "<p style='font-size: 24px; font-weight: bold;'>验证码："+code+"</p>"
            + "</body></html>";

        try {
            MailUtils.sendHtml(email, "重置密码邮件验证", htmlContent);
        } catch (Exception e) {
            log.error("邮件发送异常发送异常 => {}", e.getMessage());
            return R.fail(e.getMessage());
        }
        return R.ok();
    }

    /**
     * 验证邮件，然后重置密码
     *
     * @param emailResetPasswordBody 邮件重置密码参数
     * */
    @PostMapping("/verify")
    public R<Void> verifyEmailCode(@Validated @RequestBody EmailResetPasswordBody emailResetPasswordBody) {
        String code=RedisUtils.getCacheObject(CacheConstants.EMAIL_RESET_PASSWORD_KEY + emailResetPasswordBody.getEmail());
        if(StringUtils.isBlank(code)){
            throw new CaptchaException();
        }
        if(code.equals(emailResetPasswordBody.getEmailCode())){
            SysUser user=sysLoginService.loadUserByEmail(emailResetPasswordBody.getEmail());
            String newPassword=BCrypt.hashpw(emailResetPasswordBody.getNewPassword());
            int reset=userService.resetUserPwd(user.getUserName(),newPassword);
            if(reset>0){
                RedisUtils.deleteObject(CacheConstants.EMAIL_RESET_PASSWORD_KEY + emailResetPasswordBody.getEmail());
                return R.ok();
            }
        }else{
            return R.fail("验证码错误");
        }
        return R.fail("修改密码异常，请联系管理员");
    }

}
