package com.htht.system.controller.system;

import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.io.FileUtil;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.common.core.domain.entity.SysUser;
import com.htht.common.enums.BusinessType;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.SmUtils;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.file.MimeTypeUtils;
import com.htht.system.domain.vo.SysOssVo;
import com.htht.system.service.ISysOssService;
import com.htht.system.service.ISysUserService;
import com.htht.system.util.recordUserOperateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 个人信息 业务处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController {

    private final ISysUserService userService;
    private final ISysOssService iSysOssService;



    /**
     * 个人信息
     */
    @GetMapping
    public R<Map<String, Object>> profile() {
        SysUser user = userService.selectUserById(getUserId());
        Map<String, Object> ajax = new HashMap<>();
        ajax.put("user", user);
        ajax.put("roleGroup", userService.selectUserRoleGroup(user.getUserName()));
        ajax.put("postGroup", userService.selectUserPostGroup(user.getUserName()));
        return R.ok(ajax);
    }

    /**
     * 修改用户
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> updateProfile(@RequestBody SysUser user) {
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "修改用户'" + user.getUserName() + "失败，手机号码已存在", "失败");
            return R.fail("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "修改用户'" + user.getUserName() + "失败，邮箱账号已存在", "失败");
            return R.fail("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUserId(getUserId());
        user.setUserName(null);
        user.setPassword(null);
        user.setAvatar(null);
        user.setDeptId(null);
        if (userService.updateUserProfile(user) > 0) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "", "成功");
            return R.ok();
        }
        recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "修改个人信息异常，请联系管理员", "失败");
        return R.fail("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     *
     * @param newPassword 新密码
     * @param oldPassword 旧密码
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public R<Void> updatePwd(String oldPassword,
                             @Validated
                             @Size(min = 6,max=16,message = "新密码长度不能小于6位大于16位")
                             @Pattern(regexp = "^[a-zA-Z0-9]{6,16}$",message = "新密码仅支持字母和数字，长度6-16位")
                             String newPassword) {
        SysUser user = userService.selectUserById(LoginHelper.getUserId());
        String userName = user.getUserName();
//        System.out.println(userName);
        String password = user.getPassword();
//        System.out.println(password);
        if (!BCrypt.checkpw(oldPassword, password)) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "修改密码失败，旧密码错误", "失败");
            return R.fail("修改密码失败，旧密码错误");
        }
        if (BCrypt.checkpw(newPassword, password)) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "新密码不能与旧密码相同", "失败");
            return R.fail("新密码不能与旧密码相同");
        }

        if (userService.resetUserPwd(userName, BCrypt.hashpw(newPassword)) > 0) {
            recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "", "成功");
            return R.ok();
        }
        recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "修改密码异常，请联系管理员", "失败");
        return R.fail("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     *
     * @param avatarfile 用户头像
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> avatar(@RequestPart("avatarfile") MultipartFile avatarfile) {
        Map<String, Object> ajax = new HashMap<>();
        if (!avatarfile.isEmpty()) {
            String extension = FileUtil.extName(avatarfile.getOriginalFilename());
            if (!StringUtils.equalsAnyIgnoreCase(extension, MimeTypeUtils.IMAGE_EXTENSION)) {
                recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式", "失败");
                return R.fail("文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式");
            }
            SysOssVo oss = iSysOssService.upload(avatarfile);
            String avatar = oss.getUrl();
            if (userService.updateUserAvatar(getUsername(), avatar)) {
                ajax.put("imgUrl", avatar);
                recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "", "成功");
                return R.ok(ajax);
            }
        }
        recordUserOperateUtil.recordUserOperateLogin("个人中心", "编辑", "上传图片异常，请联系管理员", "失败");
        return R.fail("上传图片异常，请联系管理员");
    }
}
