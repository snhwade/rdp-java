package com.riskplatform.ruleconfig.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 校验集成测试基础设施本身的行为（不依赖真实 MySQL/Redis）：
 * <ul>
 *   <li>环境变量缺省解析对齐 application.yml；</li>
 *   <li>连接缺失时 require* 抛 {@link IllegalStateException}（失败而非跳过，R15.2/R15.3）。</li>
 * </ul>
 */
class IntegrationTestEnvironmentTest {

    @Test
    void defaultsAlignWithApplicationYml() {
        // 未显式设置环境变量时使用默认值；CI 上若已设置则跳过该断言以免误判
        if (System.getenv("MYSQL_HOST") == null && System.getProperty("MYSQL_HOST") == null) {
            assertThat(IntegrationTestEnvironment.mysqlHost()).isEqualTo("localhost");
        }
        assertThat(IntegrationTestEnvironment.mysqlJdbcUrl())
                .contains("risk_decision_platform")
                .startsWith("jdbc:mysql://");
        assertThat(IntegrationTestEnvironment.redisPort()).isPositive();
    }

    @Test
    void mysqlUrlOverrideTakesPrecedence() {
        // 设置完整 JDBC URL 覆盖项时，应优先于 MYSQL_HOST/PORT/DB 组合
        String override = "jdbc:mysql://db.example.com:3307/custom_db?useSSL=false";
        System.setProperty("MYSQL_URL", override);
        try {
            assertThat(IntegrationTestEnvironment.mysqlJdbcUrl()).isEqualTo(override);
        } finally {
            System.clearProperty("MYSQL_URL");
        }
    }

    @Test
    void redisUnavailableFailsNotSkips() {
        // 指向一个不可达端口，验证抛出 IllegalStateException（失败而非跳过）
        System.setProperty("REDIS_HOST", "127.0.0.1");
        System.setProperty("REDIS_PORT", "1");
        try {
            assertThatThrownBy(IntegrationTestEnvironment::requireRedisAvailable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Redis");
        } finally {
            System.clearProperty("REDIS_HOST");
            System.clearProperty("REDIS_PORT");
        }
    }
}
