package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.xss.Xss;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * <p>
 * 数据类型信息表
 * </p>
 *
 * @author wjx
 * @since 2023-05-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_data_type_info")
public class BusDataTypeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 数据类型
     */
    @NotNull(message = "数据类型不能为空")
    private String type;

    /**
     * 类型名称
     */
    @Xss(message = "数据类型名称不能包含脚本字符")
    @NotBlank(message = "数据类型名称不能为空")
    private String name;


}
