package com.htht.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

public class IDUtil {

	private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake(1, 1); // 工作节点ID
	
	public static Long getLongID() {
		return ID_GENERATOR.nextId();
	}
	
	public static String getStringID() {
		return IdUtil.randomUUID();
	}
	
	public static String getStringSimpleID() {
		return IdUtil.simpleUUID();
	}
	
	
}
