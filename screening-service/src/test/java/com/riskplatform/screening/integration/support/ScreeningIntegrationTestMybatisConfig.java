package com.riskplatform.screening.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.screening.integration.support")
public class ScreeningIntegrationTestMybatisConfig {
}
