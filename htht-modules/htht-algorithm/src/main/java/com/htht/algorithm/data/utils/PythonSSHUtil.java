package com.htht.algorithm.data.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/14 17:30
 **/
public class PythonSSHUtil {
    public static void main(String[] args) {
        String host = "192.168.2.29";  // Windows IP
        String user = "admin";  // Windows 用户名
        String password = "123456";  // Windows 密码

        // 要执行的命令（测试简单 Python 脚本）
        String command = "python C:\\test\\test1.py";  // 确保这个脚本存在

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            java.io.InputStream in = channel.getInputStream();
            channel.connect();

            // 读取输出
            byte[] tmp = new byte[1024];
            StringBuilder output = new StringBuilder();
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) break;
                try { Thread.sleep(1000); } catch (Exception e) {}
            }

            System.out.println("执行结果：\n" + output);
            channel.disconnect();
            session.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
