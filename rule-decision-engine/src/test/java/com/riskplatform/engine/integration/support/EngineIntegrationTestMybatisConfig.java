package com.riskplatform.engine.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.engine.integration.support")
public class EngineIntegrationTestMybatisConfig {
}
