package com.htht.common.core.domain.model;

import javax.validation.constraints.NotBlank;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户注册对象
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterBody extends LoginBody {

	/**
	 * PC端默认sys_user即可
	 */
    private String userType;

    @NotBlank(message = "{user.nickName.not.blank}")
    private String nickName;

    @NotBlank(message = "{user.email.not.blank}")
    private String email;

    private Long deptId;

    private String phonenumber;

    private String sex;

    private Long roleId;

}
