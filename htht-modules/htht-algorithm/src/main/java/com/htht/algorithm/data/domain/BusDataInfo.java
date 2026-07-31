package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import com.htht.common.xss.Xss;
import com.htht.algorithm.data.convert.GeometryTypeConvert;
import jodd.util.StringUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 *
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bus_data_info")
public class BusDataInfo extends BaseEntity implements Serializable,Cloneable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 数据名称
     */
    @Xss(message = "数据名称不能包含脚本字符")
    @NotBlank(message = "数据名称不能为空")
    private String name;

    /**
     * 数据类型：1-栅格；2-矢量；3-流场；4-贴图；5-栅格压缩包；6-nc格式
     */
    @NotNull(message = "数据类型不能为空")
    private Integer type;

    /**
     * 文件ID
     */
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /**
     * 数据实际时间
     */
    private Date actualTime;

    /**
     * 数据生产时间
     */
    private Date productTime;

    /**
     * 描述
     */
    private String description;

    /**
     * 资源摘要
     */
    private String remark;

    /**
     * 资源关键词（多个关键词以；分隔）
     */
    private String keywords;

    /**
     * 最小经度
     */
    private Double lngMin;

    /**
     * 最大经度
     */
    private Double lngMax;

    /**
     * 最小纬度
     */
    private Double latMin;

    /**
     * 最大纬度
     */
    private Double latMax;

    /**
     * 范围（WKT格式）
     */
    @TableField(typeHandler = GeometryTypeConvert.class)
    private String extent;

    /**
     * 入库状态（0未入库 1已入库）
     */
    private String stock;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 原始影像路径
     */
    private String originalPath;

    /**
     * 变换影像路径
     */
    private String transformPath;
    /**
     * 元数据信息
     */
    private String metaData;

    private Integer minLevel;
    private Integer maxLevel;

    /**
     * 数据类型（同数据类型绑定）
     */
    private String dataType;

    /**
     * 展示缩略图
     * @return
     */
    private String displayThumbnail;

    /**
     * 下载缩略图
     * @return
     */
    private String downloadThumbnail;

    @TableField(exist = false)
    private Long catalogId;
    @TableField(exist = false)
    private String satelliteName;
    @TableField(exist = false)
    private String satelliteLevel;

    public void setDownloadThumbnail(String downloadThumbnail) {
        if(StringUtil.isBlank(this.downloadThumbnail)){
            this.downloadThumbnail = downloadThumbnail;
        }
    }


    @Override
    public BusDataInfo clone() {
        try {
            BusDataInfo clone = (BusDataInfo) super.clone();

            // 深拷贝当前类的Date字段
            if (this.actualTime != null) {
                clone.actualTime = (Date) this.actualTime.clone();
            }
            if (this.productTime != null) {
                clone.productTime = (Date) this.productTime.clone();
            }

            // 深拷贝父类BaseEntity的字段
            // 字符串类型无需特殊处理（不可变）
            clone.setCreateBy(this.getCreateBy());
            clone.setUpdateBy(this.getUpdateBy());

            // 日期类型需要深拷贝
            if (this.getCreateTime() != null) {
                clone.setCreateTime((Date) this.getCreateTime().clone());
            }
            if (this.getUpdateTime() != null) {
                clone.setUpdateTime((Date) this.getUpdateTime().clone());
            }

            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("克隆BusDataInfo失败", e);
        }
    }
}
