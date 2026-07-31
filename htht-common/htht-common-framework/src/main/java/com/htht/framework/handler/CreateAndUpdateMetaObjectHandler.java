package com.htht.framework.handler;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpStatus;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.htht.common.core.domain.BaseEntity;
import com.htht.common.core.domain.BaseEntityDeleted;
import com.htht.common.core.domain.model.LoginUser;
import com.htht.common.enums.YesNoEnums;
import com.htht.common.exception.ServiceException;
import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * MP注入处理器
 *
 * @author Lion Li
 * @date 2021/4/25
 */
@Slf4j
public class CreateAndUpdateMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            if (ObjectUtil.isNotNull(metaObject)) {
            	if(metaObject.getOriginalObject() instanceof BaseEntity) {
            		 BaseEntity baseEntity = (BaseEntity) metaObject.getOriginalObject();
                     Date current = ObjectUtil.isNotNull(baseEntity.getCreateTime())
                         ? baseEntity.getCreateTime() : new Date();
                     baseEntity.setCreateTime(current);
                     baseEntity.setUpdateTime(current);
                     String username = StringUtils.isNotBlank(baseEntity.getCreateBy())
                         ? baseEntity.getCreateBy() : getLoginUsername();
                     // 当前已登录 且 创建人为空 则填充
                     baseEntity.setCreateBy(username);
                     // 当前已登录 且 更新人为空 则填充
                     baseEntity.setUpdateBy(username);
            	} else if(metaObject.getOriginalObject() instanceof BaseEntityDeleted) {
            		// 插入操作时自动填充
                    this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                    this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                    String username =  getLoginUsername();
                    this.strictInsertFill(metaObject, "createBy", String.class, username);
                    this.strictInsertFill(metaObject, "updateBy", String.class, username);
                    this.strictInsertFill(metaObject, "deleted", Integer.class, YesNoEnums.NO.getCode());
            	}
            }
        } catch (Exception e) {
            throw new ServiceException("自动注入异常 => " + e.getMessage(), HttpStatus.HTTP_UNAUTHORIZED);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            if (ObjectUtil.isNotNull(metaObject)) {
            	if(metaObject.getOriginalObject() instanceof BaseEntity) {
            		BaseEntity baseEntity = (BaseEntity) metaObject.getOriginalObject();
                    Date current = new Date();
                    // 更新时间填充(不管为不为空)
                    baseEntity.setUpdateTime(current);
                    String username = getLoginUsername();
                    // 当前已登录 更新人填充(不管为不为空)
                    if (StringUtils.isNotBlank(username)) {
                        baseEntity.setUpdateBy(username);
                    }
            	} else if(metaObject.getOriginalObject() instanceof BaseEntityDeleted){
                	// 更新操作时自动填充
                	this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                	this.strictUpdateFill(metaObject, "updateBy", String.class, getLoginUsername());
                }
            	
            } 
        } catch (Exception e) {
            throw new ServiceException("自动注入异常 => " + e.getMessage(), HttpStatus.HTTP_UNAUTHORIZED);
        }
    }

    /**
     * 获取登录用户名
     */
    private String getLoginUsername() {
        LoginUser loginUser;
        try {
            loginUser = LoginHelper.getLoginUser();
        } catch (Exception e) {
            log.warn("自动注入警告 => 用户未登录");
            return null;
        }
        return ObjectUtil.isNotNull(loginUser) ? loginUser.getUsername() : null;
    }

}
