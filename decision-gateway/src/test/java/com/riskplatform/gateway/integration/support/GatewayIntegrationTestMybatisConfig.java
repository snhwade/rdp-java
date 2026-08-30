package com.riskplatform.gateway.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.gateway.integration.support")
public class GatewayIntegrationTestMybatisConfig {
}
