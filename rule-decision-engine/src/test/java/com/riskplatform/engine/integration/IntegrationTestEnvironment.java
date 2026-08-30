package com.riskplatform.engine.integration;

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

/**
 * 引擎集成测试连接环境解析与连通性校验（Feature: risk-console-redesign，Requirement 15.2/15.3）。
 *
 * <p>引擎与 rule-config-service 共享同一 MySQL 库（既有约定，见 {@code DbRulePackageDefinitionAdapter}）。
 * 本类与 rule-config-service 的同名工具镜像一致，但<strong>本地于引擎模块</strong>，避免跨模块测试依赖。
 *
 * <p>约定：集成测试经环境变量连接<strong>真实 MySQL/Redis</strong>（可复用
 * {@code deploy/docker-compose.yml} 或本机已运行实例）。<strong>连接缺失视为失败而非跳过</strong>：
 * 本类的 {@code require*Available()} 方法在连通性校验不通过时抛出 {@link IllegalStateException}，
 * 集成测试因此失败，绝不调用任何 {@code Assumptions} 跳过逻辑（R15.2/R15.3）。
 *
 * <p>支持的环境变量（含默认值，默认对齐 {@code application.yml} 与本机实例）：
 * <ul>
 *   <li>{@code MYSQL_URL}（完整 JDBC URL 覆盖项；设置后优先于 {@code MYSQL_HOST/PORT/DB} 组合）</li>
 *   <li>{@code MYSQL_HOST}（默认 {@code localhost}）</li>
 *   <li>{@code MYSQL_PORT}（默认 {@code 3306}）</li>
 *   <li>{@code MYSQL_DB}（默认 {@code risk_decision_platform}）</li>
 *   <li>{@code MYSQL_USER}（默认 {@code root}）</li>
 *   <li>{@code MYSQL_PASSWORD}（默认 {@code root}）</li>
 *   <li>{@code REDIS_HOST}（默认 {@code localhost}）</li>
 *   <li>{@code REDIS_PORT}（默认 {@code 6379}）</li>
 *   <li>{@code REDIS_PASSWORD}（默认空）</li>
 * </ul>
 */
public final class IntegrationTestEnvironment {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    private IntegrationTestEnvironment() {
    }

    public static String mysqlHost() {
        return env("MYSQL_HOST", "localhost");
    }

    public static int mysqlPort() {
        return Integer.parseInt(env("MYSQL_PORT", "3306"));
    }

    public static String mysqlDatabase() {
        return env("MYSQL_DB", "risk_decision_platform");
    }

    public static String mysqlUser() {
        return env("MYSQL_USER", "root");
    }

    public static String mysqlPassword() {
        return env("MYSQL_PASSWORD", "root");
    }

    public static String mysqlJdbcUrl() {
        // 优先使用完整 JDBC URL 覆盖项 MYSQL_URL；未设置时由 MYSQL_HOST/PORT/DB 组合构造。
        String override = env("MYSQL_URL", "");
        if (!override.isEmpty()) {
            return override;
        }
        return "jdbc:mysql://" + mysqlHost() + ":" + mysqlPort() + "/" + mysqlDatabase()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
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

    /**
     * 校验真实 MySQL 可用，否则<strong>失败而非跳过</strong>。
     *
     * @throws IllegalStateException 当无法建立到真实 MySQL 的连接时
     */
    public static void requireMysqlAvailable() {
        String url = mysqlJdbcUrl();
        try (Connection ignored = DriverManager.getConnection(url, mysqlUser(), mysqlPassword())) {
            // 连接成功即可
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "集成测试要求真实 MySQL 可用，但连接失败（连接缺失视为失败而非跳过，R15.2/R15.3）。URL=" + url
                            + "。请确保本机 MySQL 运行或正确设置 MYSQL_* 环境变量。", e);
        }
    }

    /**
     * 校验真实 Redis 可用（PING/PONG），否则<strong>失败而非跳过</strong>。
     *
     * @throws IllegalStateException 当无法与真实 Redis 完成 PING 握手时
     */
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
                throw new IllegalStateException(
                        "集成测试要求真实 Redis 可用，但 PING 未返回 PONG（实际：" + reply.trim() + "）。host=" + host + ":" + port);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "集成测试要求真实 Redis 可用，但连接失败（连接缺失视为失败而非跳过，R15.2/R15.3）。host=" + host + ":" + port
                            + "。请确保本机 Redis 运行或正确设置 REDIS_* 环境变量。", e);
        }
    }

    private static void writeAuth(OutputStream out, String password) {
        try {
            out.write(("AUTH " + password + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("向 Redis 发送 AUTH 命令失败", e);
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
