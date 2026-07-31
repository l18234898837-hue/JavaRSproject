/*
package com.htht.framework.satoken;

import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.util.SaFoxUtil;
import com.htht.common.utils.StringUtils;
import com.htht.common.utils.redis.RedisUtils;

import static cn.dev33.satoken.exception.NotLoginException.*;
import static cn.dev33.satoken.exception.NotLoginException.KICK_OUT_MESSAGE;

*/
/**
 * @author liaozhenshan
 * @version 1.0
 * @date 2023/7/28 11:23
 *//*

public class NewStpLogic extends StpLogic {
    */
/**
     * 初始化 StpLogic, 并指定账号类型
     *
     * @param loginType 账号类型标识
     *//*

    public NewStpLogic(String loginType) {
        super(loginType);
    }

    //重写checkLogin方法
    public Object getLoginId() {
        // 1、先判断一下当前会话是否正在 [ 临时身份切换 ], 如果是则返回临时身份
        if(isSwitch()) {
            return getSwitchLoginId();
        }

        // 2、如果前端没有提交 token，则抛出异常: 未能读取到有效 token
        String tokenValue = getTokenValue(true);

        if (StringUtils.isEmpty(tokenValue)){
            //先尝试根据uid获取token
            tokenValue = RedisUtils.getCacheObject("token");
            if (StringUtils.isEmpty(tokenValue)){
                //checkToken
                */
/**
                 * 获取请求头的token
                 * SaRequest request = SaHolder.getRequest();
                 * request.getHeader("token");
                 * 发送okhttp请求验证token，会返回uid
                 * Object result = okhttp.get(url)
                 * String uid = result.get("uid")
                 * 根据uid获取绑定关联的当前系统用户userId
                 * userId = getUserIdByUid(uid)
                 *//*

                super.login("1","PC");
                tokenValue = super.getTokenValueNotCut();
                RedisUtils.setCacheObject("token",tokenValue);
            }
        }
        if(SaFoxUtil.isEmpty(tokenValue)) {
            throw NotLoginException.newInstance(loginType, NOT_TOKEN, NOT_TOKEN_MESSAGE, null).setCode(SaErrorCode.CODE_11011);
        }

        // 3、查找此 token 对应的 loginId，如果找不到则抛出：token 无效
        String loginId = getLoginIdNotHandle(tokenValue);
        if(SaFoxUtil.isEmpty(loginId)) {
            throw NotLoginException.newInstance(loginType, INVALID_TOKEN, INVALID_TOKEN_MESSAGE, tokenValue).setCode(SaErrorCode.CODE_11012);
        }

        // 4、如果这个 token 指向的是值是：过期标记，则抛出：token 已过期
        if(loginId.equals(NotLoginException.TOKEN_TIMEOUT)) {
            throw NotLoginException.newInstance(loginType, TOKEN_TIMEOUT, TOKEN_TIMEOUT_MESSAGE, tokenValue).setCode(SaErrorCode.CODE_11013);
        }

        // 5、如果这个 token 指向的是值是：被顶替标记，则抛出：token 已被顶下线
        if(loginId.equals(NotLoginException.BE_REPLACED)) {
            throw NotLoginException.newInstance(loginType, BE_REPLACED, BE_REPLACED_MESSAGE, tokenValue).setCode(SaErrorCode.CODE_11014);
        }

        // 6、如果这个 token 指向的是值是：被踢下线标记，则抛出：token 已被踢下线
        if(loginId.equals(NotLoginException.KICK_OUT)) {
            throw NotLoginException.newInstance(loginType, KICK_OUT, KICK_OUT_MESSAGE, tokenValue).setCode(SaErrorCode.CODE_11015);
        }

        // 7、检查此 token 的最后活跃时间是否已经超过了 active-timeout 的限制，如果是则代表其已被冻结，需要抛出：token 已被冻结
        if(isOpenCheckActiveTimeout()) {
            checkActiveTimeout(tokenValue);

            // ------ 至此，loginId 已经是一个合法的值，代表当前会话是一个正常的登录状态了

            // 8、如果配置了自动续签功能, 则: 更新这个 token 的最后活跃时间 （注意此处的续签是在续 active-timeout，而非 timeout）
            if(getConfigOrGlobal().getAutoRenew()) {
                updateLastActiveToNow(tokenValue);
            }
        }

        // 9、返回 loginId
        return loginId;

    }
}
*/
