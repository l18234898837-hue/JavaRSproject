package com.htht.common.build;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.htht.common.constant.BaseEntityField;
import com.htht.common.constant.DateFormatConstant;
import com.htht.common.enums.DelFlagEnums;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.Collectors;

public class QueryWrapperBuilder {

	/**
	 * 查询条件编辑自动装配
	 * @param <T>
	 * @param dto 查询条件对象
	 * @param entityClass	查询实体对象
	 * @param deleted	是否包含已删除的数据，true包含，false不包含
	 * @param orderby	排序字段，默认：按更新时间倒序
	 * @param sc	ASC 正序， DESC 倒序
	 * @return
	 */
    public static <T> QueryWrapper<T> build(Object dto, Class<T> entityClass, boolean deleted, String orderby, String sc) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            QueryType queryType = field.getAnnotation(QueryType.class);
            if (queryType == null) continue;

            Object value;
            try {
                value = field.get(dto);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("反射获取字段值失败", e);
            }

            if (value == null) continue;

            String columnName = resolveColumnName(field, entityClass);
            applyQueryCondition(wrapper, queryType.value(), columnName, value);
        }
        if(deleted) {
        	wrapper.eq(BaseEntityField.DEL_FLAG, DelFlagEnums.DELETED.getCode());
        } else {
        	wrapper.eq(BaseEntityField.DEL_FLAG, DelFlagEnums.EXISTS.getCode());
        }
        if(StrUtil.isNotEmpty(sc) && "ASC".equalsIgnoreCase(sc)) {
        	if(StrUtil.isNotEmpty(orderby)) {
        		wrapper.orderByAsc(orderby);
        	} else {
        		wrapper.orderByAsc(BaseEntityField.UPDATE_TIME);
        	}
        } else if(StrUtil.isNotEmpty(sc) && "DESC".equals(sc.toUpperCase())) {
        	if(StrUtil.isNotEmpty(orderby)) {
        		wrapper.orderByDesc(orderby);
        	} else {
        		wrapper.orderByDesc(BaseEntityField.UPDATE_TIME);
        	}
        } else {
        	wrapper.orderByDesc(BaseEntityField.UPDATE_TIME);
        }
        return wrapper;
    }

    /**
	 * 查询条件编辑自动装配
	 * @param <T>
	 * @param dto 查询条件对象
	 * @param entityClass	查询实体对象
	 * @return
	 */
    public static <T> QueryWrapper<T> build(Object dto, Class<T> entityClass) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        Field[] fields = dto.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            QueryType queryType = field.getAnnotation(QueryType.class);
            if (queryType == null) continue;

            Object value;
            try {
                value = field.get(dto);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("反射获取字段值失败", e);
            }

            if (value == null) continue;

            String columnName = resolveColumnName(field, entityClass);
            applyQueryCondition(wrapper, queryType.value(), columnName, value);
        }
        return wrapper;
    }

    private static String resolveColumnName(Field field, Class<?> entityClass) {
        // 通过 MyBatis-Plus 的 TableField 注解或字段名转下划线获取列名
        try {
            Field entityField = entityClass.getDeclaredField(field.getName());
            TableField tableField = entityField.getAnnotation(TableField.class);
            return (tableField != null && !tableField.value().isEmpty())
                   ? tableField.value()
                   : StringUtils.camelToUnderline(field.getName());
        } catch (NoSuchFieldException e) {
            return StringUtils.camelToUnderline(field.getName());
        }
    }

    private static <T> void applyQueryCondition(QueryWrapper<T> wrapper, QueryType.QueryMode mode, String columnName, Object value) {
        switch (mode) {
            case IN:
                if (value instanceof Collection) {
                    wrapper.inSql(columnName, buildInClause((Collection<?>) value));
                }
                break;
            case EQ:
            	if(value instanceof LocalDateTime) {
            		// 不想搞得太复杂， 写死PG数据库写法
            		wrapper.eq(ObjUtil.isNotEmpty(value), "to_char(" + columnName + ", 'YYYY-MM-DD HH24:MI:SS') " , value.toString().replace("T", " "));
            	} else {
            		wrapper.eq(ObjUtil.isNotEmpty(value), columnName, value);
            	}
                break;
            case LIKE:
                wrapper.like(ObjUtil.isNotEmpty(value), columnName, value);
                break;
            case LE:
            	if(value instanceof LocalDateTime) {
            		wrapper.le(ObjUtil.isNotEmpty(value), columnName, LocalDateTimeUtil.parse(value.toString(), DateFormatConstant.NORMAL));
            	} else {
            		wrapper.le(ObjUtil.isNotEmpty(value), columnName, value);
            	}
                break;
                /**
                 * 大于等于
                 */
            case GE:
            	if(value instanceof LocalDateTime) {
            		wrapper.ge(ObjUtil.isNotEmpty(value), columnName, LocalDateTimeUtil.parse(value.toString(), DateFormatConstant.NORMAL));
            	} else {
            		wrapper.ge(ObjUtil.isNotEmpty(value), columnName, value);
            	}
                break;
            case GT:
                wrapper.gt(ObjUtil.isNotEmpty(value), columnName, value);
                break;
            case LT:
                wrapper.lt(ObjUtil.isNotEmpty(value), columnName, value);
                break;
            case NE:
                wrapper.ne(ObjUtil.isNotEmpty(value), columnName, value);
                break;
            case NOT_LIKE:
                wrapper.notLike(ObjUtil.isNotEmpty(value), columnName, value);
                break;
            case BETWEEN:
                // 处理数组类型的区间查询
                if (value instanceof Object[]) {
                    Object[] array = (Object[]) value;
                    if (array.length >= 2) {
                        Object start = array[0];
                        Object end = array[1];
                        if (ObjUtil.isNotEmpty(start) && ObjUtil.isNotEmpty(end)) {
                            // 两个值都存在，使用 BETWEEN
                            if(value instanceof LocalDateTime) {
                            	wrapper.between(columnName, LocalDateTimeUtil.parse(start.toString(), DateFormatConstant.NORMAL), LocalDateTimeUtil.parse(end.toString(), DateFormatConstant.NORMAL));
                        	} else {
                        		wrapper.between(columnName, start, end);
                        	}
                        } else if (ObjUtil.isNotEmpty(start)) {
                            // 只有开始值，使用 >=
                            if(value instanceof LocalDateTime) {
                        		wrapper.ge( columnName, LocalDateTimeUtil.parse(start.toString(), DateFormatConstant.NORMAL));
                        	} else {
                        		wrapper.ge(columnName, start);
                        	}
                        } else if (ObjUtil.isNotEmpty(end)) {
                            // 只有结束值，使用 <=
                            if(value instanceof LocalDateTime) {
                        		wrapper.le(columnName, LocalDateTimeUtil.parse(end.toString(), DateFormatConstant.NORMAL));
                        	} else {
                        		wrapper.le(columnName, end);
                        	}
                        }
                    }
                }
                break;
		default:
			break;
        }
    }


    private static String buildInClause(Collection<?> values) {
        return values.stream()
                   .map(v -> "'" + v.toString() + "'")
                   .collect(Collectors.joining(",", "(", ")"));
    }
}
