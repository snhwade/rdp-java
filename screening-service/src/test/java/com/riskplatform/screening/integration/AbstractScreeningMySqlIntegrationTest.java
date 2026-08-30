package com.riskplatform.screening.integration;

import com.riskplatform.screening.integration.support.ScreeningIntegrationTestMybatisConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ScreeningIntegrationTestMybatisConfig.class)
public abstract class AbstractScreeningMySqlIntegrationTest {

    @BeforeAll
    void requireRealMySql() {
        IntegrationTestEnvironment.requireMysqlAvailable();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestEnvironment::mysqlJdbcUrl);
        registry.add("spring.datasource.username", IntegrationTestEnvironment::mysqlUser);
        registry.add("spring.datasource.password", IntegrationTestEnvironment::mysqlPassword);
    }
}
