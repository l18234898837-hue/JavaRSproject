package com.htht.common.build;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface QueryType {

	QueryMode value() default QueryMode.EQ;
	String fieldName() default "";

    public enum QueryMode {
    	/** 精确查询 */
    	EQ,

    	/** 模糊查询 */
    	LIKE,

    	/** 范围查询 */
    	BETWEEN,

    	/** 大于 */
    	GT,

    	/** 小于 */
    	LT,

    	/** 大于等于 */
    	GE,

    	/** 小于等于 */
    	LE,

    	/** 包含 */
    	IN,

    	/** 不等于 */
    	NE,

    	/** 不包含 */
        NOT_LIKE
    }
}
