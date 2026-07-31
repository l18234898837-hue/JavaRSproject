package com.htht.business.user.domain;

import com.htht.common.constant.UserConstants;
import com.htht.common.core.domain.model.EmailLoginBody;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * TODO
 *
 * @author msfy
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class EmailResetPasswordBody extends EmailLoginBody {
    /*
    * 新密码
    * */
    @NotBlank(message = "{user.new.password.not.blank}")
    @Length(min = UserConstants.PASSWORD_MIN_LENGTH, max = UserConstants.PASSWORD_MAX_LENGTH, message = "{user.password.length.valid}")
    private String newPassword;
}
