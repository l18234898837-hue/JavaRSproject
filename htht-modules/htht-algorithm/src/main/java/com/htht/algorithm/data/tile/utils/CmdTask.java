package com.htht.algorithm.data.tile.utils;

import com.htht.common.exception.UtilException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class CmdTask implements Runnable {

    private String[] command;
    private String[] env;
    private CmdCallBack callBack;


    public CmdTask(String[] command, String[] env, CmdCallBack callBack) {
        this.command = command;
        this.env = env;
        this.callBack = callBack;
    }

    @Override
    public void run() {
        Process process = null;
        int exitVal = 0;

        try {
            process = Runtime.getRuntime().exec(command, env);
            //Runtime.exec()创建的子进程公用父进程的流，不同平台上，父进程的stream buffer可能被打满导致子进程阻塞，从而永远无法返回
            //针对这种情况，我们只需要将子进程的stream重定向出来即可
            new RedirCmdStreamThread(process.getInputStream(), "INFO").start();
            new RedirCmdStreamThread(process.getErrorStream(), "ERR").start();
            exitVal = process.waitFor();
            if (exitVal == 0) {
                callBack.success();
            } else {
                callBack.failed();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (exitVal != 0) {
            callBack.failed();
            throw new UtilException("cmd任务执行失败");
        }
    }


    static class RedirCmdStreamThread extends Thread {
        InputStream is;
        String printType;

        RedirCmdStreamThread(InputStream is, String printType) {
            this.is = is;
            this.printType = printType;
        }

        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    log.info(printType + ">" + line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        String[] cmd = new String[]{
            "tippecanoe -e G://test/tile/test -pC -z1 -z12 -f G://test//unzip//1669609032945037314/省.json",
        };
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            new RedirCmdStreamThread(process.getInputStream(), "INFO").start();
            new RedirCmdStreamThread(process.getErrorStream(), "ERR").start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
