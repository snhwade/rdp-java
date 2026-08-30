package com.riskplatform.ruleconfig.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 集成测试基类：同时连接<strong>真实 MySQL 与真实 Redis</strong>（经环境变量）。
 *
 * <p>用于需要 Redis 的集成测试（规则三态执行 R7.10、评分定级 R12.8、直接定级 R13.7、
 * 迁移与种子幂等 R16.2 等）。启动前对 MySQL 与 Redis 均做连通性校验，任一不可用即
 * <strong>失败而非跳过</strong>（R15.2/R15.3）。
 *
 * <p>Redis 连接信息经 {@code REDIS_HOST}/{@code REDIS_PORT}/{@code REDIS_PASSWORD} 环境变量解析，
 * 默认对齐本机实例（{@code localhost:6379}，无密码）。
 */
public abstract class AbstractMySqlRedisIntegrationTest extends AbstractMySqlIntegrationTest {

    /**
     * 启动前置：真实 Redis 不可用即失败而非跳过（R15.2/R15.3）。
     */
    @BeforeAll
    void requireRealRedis() {
        IntegrationTestEnvironment.requireRedisAvailable();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", IntegrationTestEnvironment::redisHost);
        registry.add("spring.data.redis.port", IntegrationTestEnvironment::redisPort);
        registry.add("spring.data.redis.password",
                () -> IntegrationTestEnvironment.redisPassword().orElse(""));
    }
}
