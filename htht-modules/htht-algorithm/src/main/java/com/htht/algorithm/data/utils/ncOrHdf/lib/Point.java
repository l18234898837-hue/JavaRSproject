package com.htht.algorithm.data.utils.ncOrHdf.lib;

/**
 * @author Gersy
 * @date 2020/7/9
 * 图片像素点位置
 */
public class Point {
    public int x;
    public int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
