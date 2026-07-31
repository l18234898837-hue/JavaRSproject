package com.htht.algorithm.algorithm.util;

import com.htht.algorithm.algorithm.event.AlgorithmRunRecordEndEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author: MSFY
 * Description: Python/外部命令执行工具，修复长进程日志阻塞问题
 * DateTime: 2025/10/13 10:08
 **/
public class PythonUtil {
    // 单例线程池，异步读取输出流，避免串行阻塞
    private static final ExecutorService STREAM_POOL = Executors.newCachedThreadPool();

    private static void runEnd(AlgorithmRunRecordEndEvent event, String logStr, String runStatus) {
        event.setLog(logStr)
            .setEndTime(LocalDateTime.now())
            .setRunStatus(runStatus)
            .sendEvent();
    }

    public static void run(String pythonPath, String scriptPath, boolean isWaitFor, AlgorithmRunRecordEndEvent event, String... args) {
        String[] command = new String[2 + args.length];
        command[0] = pythonPath;
        command[1] = scriptPath;
        System.arraycopy(args, 0, command, 2, args.length);
        ProcessBuilder pb = new ProcessBuilder(command);
        // 设置工作目录
        pb.directory(Path.of(scriptPath).getParent().toFile());

        execProcess(pb, event, isWaitFor);
    }

    public static void run(String pythonPath, String scriptPath, String workPath, boolean isWaitFor, AlgorithmRunRecordEndEvent event, String... args) {
        String[] command = new String[2 + args.length];
        command[0] = pythonPath;
        command[1] = scriptPath;
        System.arraycopy(args, 0, command, 2, args.length);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workPath));

        execProcess(pb, event, isWaitFor);
    }

    /**
     * 专门执行系统命令（MATLAB、l2gen、bash、外部命令）
     * 第二个参数 workDir = 工作目录，不会拼到命令里！
     */
    public static void runCommand(
        String commandPath,
        String workDir,
        boolean isWaitFor,
        AlgorithmRunRecordEndEvent event,
        String... args
    ) {
        String[] command = new String[1 + args.length];
        command[0] = commandPath;
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null && !workDir.trim().isEmpty()) {
            pb.directory(new File(workDir));
        }

        execProcess(pb, event, isWaitFor);
    }

    /**
     * 专门执行系统命令（MATLAB、l2gen、bash、外部命令）
     * 第二个参数 workDir = 工作目录，不会拼到命令里！
     */
    public static void runCommand(
        String commandPath,
        boolean isWaitFor,
        AlgorithmRunRecordEndEvent event,
        String... args
    ) {
        String[] command = new String[1 + args.length];
        command[0] = commandPath;
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Path.of(commandPath).getParent().toFile());
        execProcess(pb, event, isWaitFor);
    }

    /**
     * 统一进程执行逻辑（核心修复点：双线程异步读取流，收集完整日志）
     */
    private static void execProcess(ProcessBuilder pb, AlgorithmRunRecordEndEvent event, boolean isWaitFor) {
        Process process = null;
        // 线程安全日志收集容器
        StringBuilder logSb = new StringBuilder();
        // 计数器：等待stdout、stderr两个读取线程全部完成
        CountDownLatch latch = new CountDownLatch(2);
        // 运行状态
        String runStatus = "运行完成";
        try {
            process = pb.start();
            final Process finalProcess = process;

            // 异步读取标准输出
            STREAM_POOL.submit(() -> {
                try {
                    handleStream(finalProcess.getInputStream(), "OUTPUT", logSb);
                } finally {
                    latch.countDown();
                }
            });
            // 异步读取错误输出
            STREAM_POOL.submit(() -> {
                try {
                    handleStream(finalProcess.getErrorStream(), "ERROR", logSb);
                } finally {
                    latch.countDown();
                }
            });

            if (isWaitFor) {
                // 等待进程结束，增加30分钟超时防止永久阻塞
                long timeoutMs = 30 * 60 * 1000;
                boolean finish = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!finish) {
                    runStatus = "运行超时";
                    System.err.println("进程执行超时，强制销毁进程");
                    process.destroy();
                } else {
                    int exitCode = process.exitValue();
                    // 非0退出码标记异常
                    if (exitCode != 0) {
                        runStatus = "运行异常";
                    }
                    System.out.println("命令/Python脚本执行完成，退出码: " + exitCode);
                }
            }
        } catch (Exception e) {
            runStatus = "启动失败";
            String errMsg = "执行异常：" + e.getMessage() + "\n";
            logSb.insert(0, errMsg);
            System.err.println(errMsg);
            e.printStackTrace();
        } finally {
            // 等待两个日志读取线程执行完毕，避免日志丢失
            // 最多等待5秒收集日志，超过直接放行，避免永久阻塞
            try {
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断标记，规范写法
            }
            // 推送事件，把完整日志传给事件，由事件监听器入库
            runEnd(event, logSb.toString(), runStatus);
            // 资源释放
            if (process != null) {
                process.destroy();
                try {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                    process.getOutputStream().close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    /**
      * 流读取逻辑：实时打印 + 收集日志到StringBuilder
      */
    private static void handleStream(InputStream inputStream, String type, StringBuilder logSb) {
        // 强制UTF-8编码，解决中文问号乱码
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String logLine = String.format("[%s] %s%n", type, line);
                System.out.print(logLine);
                logSb.append(logLine);
            }
        } catch (IOException e) {
            String errLine = String.format("[%s] 流读取异常: %s%n", type, e.getMessage());
            System.err.print(errLine);
            logSb.append(errLine);
        }
    }
    /**
     * 程序关闭时销毁线程池，释放资源（Spring项目可注册销毁钩子）
     */
    public static void shutdownPool() {
        STREAM_POOL.shutdownNow();
    }
}
