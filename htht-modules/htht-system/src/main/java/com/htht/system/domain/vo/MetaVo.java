package com.htht.system.domain.vo;

import com.htht.common.utils.StringUtils;
import lombok.Data;

/**
 * 路由显示信息
 *
 * @author ruoyi
 */

@Data
public class MetaVo {

    /**
     * 设置该路由在侧边栏和面包屑中展示的名字
     */
    private String title;

    /**
     * 设置该路由的图标，对应路径src/assets/icons/svg
     */
    private String icon;

    /**
     * 设置为true，则不会被 <keep-alive>缓存
     */
    private boolean noCache;

    /**
     * 设置为true，则不会被 <keep-alive>缓存
     */
    private boolean keepAlive;

    /**
     * 内链地址（http(s)://开头）
     */
    private String link;

    /**
     * 内链地址（http(s)://开头）
     */
    private String frameSrc;

    /**
     * 是否显示父级菜单
     */
    private Boolean showParent;

    /**
     * 是否显示菜单
     */
    private Boolean showLink;

    /**
     * 是否为外链（0是 1否）
     */
    private String isFrame;

    public MetaVo(String title, String icon, Boolean showParent, Boolean showLink,String isFrame) {
        this.title = title;
        this.icon = icon;
        this.isFrame = isFrame;

        if(showParent == null) {
            this.showParent = true;
        }else {
            this.showParent = showParent;
        }
    }

    public MetaVo(String title, String icon, boolean noCache, Boolean showParent, Boolean showLink,String isFrame) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        this.isFrame = isFrame;

        this.keepAlive = !noCache;

        if(showParent == null) {
            this.showParent = true;
        }else {
            this.showParent = showParent;
        }
    }

    public MetaVo(String title, String icon, String link, Boolean showParent, Boolean showLink,String isFrame) {
        this.title = title;
        this.icon = icon;
        this.link = link;
        this.isFrame = isFrame;

        this.frameSrc = link;

        if(showParent == null) {
            this.showParent = true;
        }else {
            this.showParent = showParent;
        }
    }

    public MetaVo(String title, String icon, boolean noCache, String link, Boolean showParent, Boolean showLink,String isFrame) {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        this.showLink = showLink;
        this.isFrame = isFrame;

        this.keepAlive = !noCache;
        if (StringUtils.ishttp(link)) {
            this.link = link;

            this.frameSrc = link;
        }

        if(showParent == null) {
            this.showParent = true;
        }else {
            this.showParent = showParent;
        }
    }

}
