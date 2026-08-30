package com.riskplatform.bff.integration;

import com.riskplatform.bff.integration.support.BffIntegrationTestMybatisConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BffIntegrationTestMybatisConfig.class)
public abstract class AbstractBffMySqlRedisIntegrationTest {

    @BeforeAll
    void requireRealInfrastructure() {
        IntegrationTestEnvironment.requireMysqlAvailable();
        IntegrationTestEnvironment.requireRedisAvailable();
    }

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestEnvironment::mysqlJdbcUrl);
        registry.add("spring.datasource.username", IntegrationTestEnvironment::mysqlUser);
        registry.add("spring.datasource.password", IntegrationTestEnvironment::mysqlPassword);
        registry.add("spring.data.redis.host", IntegrationTestEnvironment::redisHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(IntegrationTestEnvironment.redisPort()));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "1000");
        registry.add("indicator.accumulate.enabled", () -> "false");
        registry.add("indicator.storage.write-redis", () -> "false");
        registry.add("indicator.storage.write-es", () -> "false");
        registry.add("rdp.integration.mode", () -> "standalone");
    }
}
