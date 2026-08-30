package com.riskplatform.bff.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/** 集成测试 MySQL/Redis 环境解析与连通性校验。 */
public final class IntegrationTestEnvironment {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private IntegrationTestEnvironment() {
    }

    public static String mysqlJdbcUrl() {
        String override = env("MYSQL_URL", "");
        if (!override.isEmpty()) {
            return override;
        }
        String host = env("MYSQL_HOST", "localhost");
        int port = Integer.parseInt(env("MYSQL_PORT", "3306"));
        String db = env("MYSQL_DB", "risk_decision_platform");
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    public static String mysqlUser() {
        return env("MYSQL_USER", "root");
    }

    public static String mysqlPassword() {
        return env("MYSQL_PASSWORD", "root");
    }

    public static String redisHost() {
        return env("REDIS_HOST", "localhost");
    }

    public static int redisPort() {
        return Integer.parseInt(env("REDIS_PORT", "6379"));
    }

    public static Optional<String> redisPassword() {
        String pwd = env("REDIS_PASSWORD", "");
        return pwd.isEmpty() ? Optional.empty() : Optional.of(pwd);
    }

    public static void requireMysqlAvailable() {
        String url = mysqlJdbcUrl();
        try (Connection ignored = DriverManager.getConnection(url, mysqlUser(), mysqlPassword())) {
            // ok
        } catch (SQLException e) {
            throw new IllegalStateException("集成测试要求真实 MySQL 可用，但连接失败。URL=" + url, e);
        }
    }

    public static void requireRedisAvailable() {
        String host = redisHost();
        int port = redisPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            redisPassword().ifPresent(pwd -> writeAuth(out, pwd));
            out.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            byte[] buffer = new byte[64];
            int read = in.read(buffer);
            String reply = read > 0 ? new String(buffer, 0, read, StandardCharsets.US_ASCII) : "";
            if (!reply.toUpperCase().contains("PONG")) {
                throw new IllegalStateException("Redis PING 未返回 PONG: " + reply.trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "集成测试要求真实 Redis 可用，但连接失败。host=" + host + ":" + port, e);
        }
    }

    private static void writeAuth(OutputStream out, String password) {
        try {
            out.write(("AUTH " + password + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("向 Redis 发送 AUTH 失败", e);
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
