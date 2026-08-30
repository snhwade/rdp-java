package com.riskplatform.gateway.integration;

import com.riskplatform.gateway.integration.support.GatewayIntegrationTestMybatisConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(GatewayIntegrationTestMybatisConfig.class)
public abstract class AbstractGatewayMySqlRedisIntegrationTest {

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
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "1000");
    }
}
