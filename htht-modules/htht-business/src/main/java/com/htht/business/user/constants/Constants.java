package com.htht.business.user.constants;

/**
 * TODO
 *
 * @author msfy
 */
public interface Constants {

    /**
    * 发送邮件中网站地址
    */
    String WEBSITE_URL="http://localhost:8080";

    /**
     * 请求后面定向地址
     */
    String EMAIL_REDIRECT_URL="/EmailResetPassword";

    /**
    * 邮箱重置密码验证有效期
    */
    Integer EMAIL_RESET_PASSWORD_EXPIRATION=30;
}
