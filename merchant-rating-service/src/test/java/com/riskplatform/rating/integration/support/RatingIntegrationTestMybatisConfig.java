package com.riskplatform.rating.integration.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@MapperScan("com.riskplatform.rating.integration.support")
public class RatingIntegrationTestMybatisConfig {
}
