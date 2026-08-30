package com.riskplatform.indicator.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.indicator.integration.support")
public class IndicatorStoreIntegrationTestMybatisConfig {
}
