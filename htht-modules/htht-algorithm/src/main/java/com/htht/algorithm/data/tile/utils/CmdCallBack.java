package com.htht.algorithm.data.tile.utils;

public interface CmdCallBack {

    /**
     * cmd执行成功回调
     */
    void success();

    /**
     * cmd执行失败回调
     */
    void failed();
}
