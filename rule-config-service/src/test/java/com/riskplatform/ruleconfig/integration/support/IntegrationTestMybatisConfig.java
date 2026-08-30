package com.riskplatform.ruleconfig.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.ruleconfig.integration.support")
public class IntegrationTestMybatisConfig {
}
