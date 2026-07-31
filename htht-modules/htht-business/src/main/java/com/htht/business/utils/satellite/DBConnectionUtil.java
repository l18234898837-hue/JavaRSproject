package com.htht.business.utils.satellite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据库连接工具类
 * 支持多种数据库类型：PostgreSQL, MySQL, Oracle, SQL Server等
 */
public class DBConnectionUtil {

    // 数据库类型常量
    public static final String POSTGRESQL = "postgresql";
//    public static final String MYSQL = "mysql";
//    public static final String ORACLE = "oracle";
//    public static final String SQLSERVER = "sqlserver";

    // 默认连接配置
    private static final Properties DEFAULT_CONFIG = new Properties();

    // 数据库驱动类映射
    private static final ConcurrentHashMap<String, String> DRIVER_MAP = new ConcurrentHashMap<>();

    // 数据库URL格式映射
    private static final ConcurrentHashMap<String, String> URL_FORMAT_MAP = new ConcurrentHashMap<>();

    static {
        // 初始化默认配置
        DEFAULT_CONFIG.setProperty("maxConnections", "10");
        DEFAULT_CONFIG.setProperty("connectionTimeout", "30000");
        DEFAULT_CONFIG.setProperty("idleTimeout", "600000");

        // 注册数据库驱动类
        DRIVER_MAP.put(POSTGRESQL, "org.postgresql.Driver");
//        DRIVER_MAP.put(MYSQL, "com.mysql.cj.jdbc.Driver");
//        DRIVER_MAP.put(ORACLE, "oracle.jdbc.driver.OracleDriver");
//        DRIVER_MAP.put(SQLSERVER, "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // 注册数据库URL格式
        URL_FORMAT_MAP.put(POSTGRESQL, "jdbc:postgresql://%s:%d/%s");
//        URL_FORMAT_MAP.put(MYSQL, "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC");
//        URL_FORMAT_MAP.put(ORACLE, "jdbc:oracle:thin:@%s:%d:%s");
//        URL_FORMAT_MAP.put(SQLSERVER, "jdbc:sqlserver://%s:%d;databaseName=%s");

        // 尝试注册所有驱动
        registerAllDrivers();
    }

    /**
     * 注册所有数据库驱动
     */
    private static void registerAllDrivers() {
        for (String driverClass : DRIVER_MAP.values()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                // 驱动未找到，可能是未添加相关依赖，继续尝试其他驱动
                System.err.println("警告: 无法加载数据库驱动 " + driverClass +
                    "，请确保已添加相关依赖: " + e.getMessage());
            }
        }
    }

    /**
     * 获取数据库连接（使用默认配置）
     * @param dbType 数据库类型
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param database 数据库名称
     * @param username 用户名
     * @param password 密码
     * @return 数据库连接
     * @throws SQLException 如果连接失败
     */
    public static Connection getConnection(String dbType, String host, int port,
                                           String database, String username, String password) throws SQLException {
        return getConnection(dbType, host, port, database, username, password, new Properties());
    }

    /**
     * 获取数据库连接（使用自定义配置）
     * @param dbType 数据库类型
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param database 数据库名称
     * @param username 用户名
     * @param password 密码
     * @param config 自定义连接配置
     * @return 数据库连接
     * @throws SQLException 如果连接失败
     */
    public static Connection getConnection(String dbType, String host, int port,
                                           String database, String username, String password,
                                           Properties config) throws SQLException {
        // 验证参数
        validateParameters(dbType, host, port, database, username, password);

        // 获取驱动类
        String driverClass = DRIVER_MAP.get(dbType.toLowerCase());
        if (driverClass == null) {
            throw new SQLException("不支持的数据库类型: " + dbType);
        }

        // 构建连接URL
        String url = buildConnectionUrl(dbType, host, port, database);

        // 合并配置
        Properties connectionProps = new Properties(DEFAULT_CONFIG);
        if (config != null) {
            connectionProps.putAll(config);
        }
        connectionProps.setProperty("user", username);
        connectionProps.setProperty("password", password);

        try {
            // 获取连接
            return DriverManager.getConnection(url, connectionProps);
        } catch (SQLException e) {
            throw new SQLException("无法连接到数据库: " + e.getMessage(), e);
        }
    }

    /**
     * 构建数据库连接URL
     * @param dbType 数据库类型
     * @param host 主机地址
     * @param port 端口号
     * @param database 数据库名称
     * @return 连接URL
     */
    private static String buildConnectionUrl(String dbType, String host, int port, String database) {
        String format = URL_FORMAT_MAP.get(dbType.toLowerCase());
        if (format == null) {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
        return String.format(format, host, port, database);
    }

    /**
     * 验证连接参数
     * @param dbType 数据库类型
     * @param host 主机地址
     * @param port 端口号
     * @param database 数据库名称
     * @param username 用户名
     * @param password 密码
     */
    private static void validateParameters(String dbType, String host, int port,
                                           String database, String username, String password) {
        if (dbType == null || dbType.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库类型不能为空");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("主机地址不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口号无效: " + port);
        }
        if (database == null || database.trim().isEmpty()) {
            throw new IllegalArgumentException("数据库名称不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
    }

    /**
     * 关闭数据库连接
     * @param conn 要关闭的连接
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("关闭数据库连接时出错: " + e.getMessage());
            }
        }
    }

    /**
     * 测试数据库连接
     * @param dbType 数据库类型
     * @param host 数据库主机地址
     * @param port 数据库端口
     * @param database 数据库名称
     * @param username 用户名
     * @param password 密码
     * @return 连接是否成功
     */
    public static boolean testConnection(String dbType, String host, int port,
                                         String database, String username, String password) {
        Connection conn = null;
        try {
            conn = getConnection(dbType, host, port, database, username, password);
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        } finally {
            closeConnection(conn);
        }
    }

    /**
     * 添加自定义数据库驱动
     * @param dbType 数据库类型
     * @param driverClass 驱动类名
     * @param urlFormat URL格式
     */
    public static void addCustomDriver(String dbType, String driverClass, String urlFormat) {
        if (dbType != null && driverClass != null) {
            DRIVER_MAP.put(dbType.toLowerCase(), driverClass);
            if (urlFormat != null) {
                URL_FORMAT_MAP.put(dbType.toLowerCase(), urlFormat);
            }

            // 尝试注册新驱动
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                System.err.println("警告: 无法加载自定义数据库驱动 " + driverClass + ": " + e.getMessage());
            }
        }
    }

    /**
     * 移除数据库驱动
     * @param dbType 数据库类型
     */
    public static void removeDriver(String dbType) {
        if (dbType != null) {
            DRIVER_MAP.remove(dbType.toLowerCase());
            URL_FORMAT_MAP.remove(dbType.toLowerCase());
        }
    }

    /**
     * 获取支持的数据库类型列表
     * @return 支持的数据库类型数组
     */
    public static String[] getSupportedDatabaseTypes() {
        return DRIVER_MAP.keySet().toArray(new String[0]);
    }
}
