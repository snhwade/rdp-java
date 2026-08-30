package com.riskplatform.bff.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.bff.integration.support")
public class BffIntegrationTestMybatisConfig {
}
