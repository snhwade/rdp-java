package com.riskplatform.engine.integration;

import com.riskplatform.engine.integration.support.EngineIntegrationTestMybatisConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 引擎集成测试基类：运行真实引擎 Spring 上下文，连接<strong>真实 MySQL 与真实 Redis</strong>（经环境变量）。
 *
 * <p>用于规则三态执行集成测试（任务 9.3 / R7.10 / R15.1 / R15.3）。引擎与 rule-config-service 共享
 * 同一 MySQL 库，本类启动真实 {@code @SpringBootTest} 上下文并将数据源指向真实库；启动前对
 * MySQL 与 Redis 均做连通性校验，任一不可用即<strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p>引擎自身不直接依赖 {@code spring-boot-starter-data-redis}，故本基类不注入 Redis 自动配置属性，
 * 仅强制校验真实 Redis 可用以满足"真实 MySQL/Redis"硬性约束（R7.10/R15.3）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(EngineIntegrationTestMybatisConfig.class)
public abstract class AbstractEngineMySqlRedisIntegrationTest {

    /**
     * 启动前置：真实 MySQL 与真实 Redis 不可用即失败而非跳过（R15.2/R15.3）。
     */
    @BeforeAll
    void requireRealInfrastructure() {
        IntegrationTestEnvironment.requireMysqlAvailable();
        IntegrationTestEnvironment.requireRedisAvailable();
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
