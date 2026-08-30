package com.riskplatform.rating.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** 集成测试 MySQL 环境解析与连通性校验。 */
public final class IntegrationTestEnvironment {

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

    public static void requireMysqlAvailable() {
        String url = mysqlJdbcUrl();
        try (Connection ignored = DriverManager.getConnection(url, mysqlUser(), mysqlPassword())) {
            // ok
        } catch (SQLException e) {
            throw new IllegalStateException("集成测试要求真实 MySQL 可用，但连接失败。URL=" + url, e);
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
