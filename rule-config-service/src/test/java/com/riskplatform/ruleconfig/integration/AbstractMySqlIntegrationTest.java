package com.riskplatform.ruleconfig.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.riskplatform.ruleconfig.integration.support.IntegrationTestMybatisConfig;

/**
 * 集成测试基类：连接<strong>真实 MySQL</strong>（经环境变量），运行真实 Spring 上下文与 Flyway 迁移。
 *
 * <p>本期 risk-console-redesign 的硬性集成测试（任务 2.6/3.4/4.5/5.5/8.4/11.4/12.5/14.6/16.2 等）
 * 继承本类即可获得：
 * <ul>
 *   <li>真实数据源（{@link IntegrationTestEnvironment} 解析 {@code MYSQL_*} 环境变量，
 *       默认对齐 {@code application.yml}）；</li>
 *   <li>启动前连通性校验：MySQL 不可用时<strong>抛异常使测试失败，绝不跳过</strong>（R15.2/R15.3）。</li>
 * </ul>
 *
 * <p>需要 Redis 的集成测试（如规则三态执行 7.10、评分/直接定级 12.8/13.7、迁移种子幂等 16.2）
 * 应改为继承 {@link AbstractMySqlRedisIntegrationTest}。
 *
 * <h3>迁移与种子约定（本期）</h3>
 * <ul>
 *   <li>本期 Flyway 版本化迁移从 <strong>V19</strong> 起（既有迁移已到 V18），全部走 {@code db/migration}，
 *       幂等且保留既有数据（R14.3）。</li>
 *   <li>种子数据使用可重复迁移 <strong>{@code R__seed_*.sql}</strong>，以幂等 upsert
 *       （{@code INSERT ... ON DUPLICATE KEY UPDATE} 或唯一键冲突即跳过）实现，重复执行不产生重复记录（R15.6）。</li>
 *   <li>迁移与种子脚本命名一律中性，禁止出现厂商专有名词（R1.3）。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(IntegrationTestMybatisConfig.class)
public abstract class AbstractMySqlIntegrationTest {

    /**
     * 启动前置：真实 MySQL 不可用即失败而非跳过（R15.2/R15.3）。
     */
    @BeforeAll
    void requireRealMySql() {
        IntegrationTestEnvironment.requireMysqlAvailable();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestEnvironment::mysqlJdbcUrl);
        registry.add("spring.datasource.username", IntegrationTestEnvironment::mysqlUser);
        registry.add("spring.datasource.password", IntegrationTestEnvironment::mysqlPassword);
        // 无 Kafka 环境下不阻断集成测试启动
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "1000");
    }
}
