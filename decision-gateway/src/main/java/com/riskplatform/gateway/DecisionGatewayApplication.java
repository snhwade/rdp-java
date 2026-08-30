package com.riskplatform.gateway;

import org.mybatis.spring.annotation.MapperScan;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@EnableConfigurationProperties(AgentLlmProperties.class)
@MapperScan(
        basePackages = {
                "com.riskplatform.gateway.infrastructure.order",
                "com.riskplatform.gateway.infrastructure.decisionlog",
                "com.riskplatform.gateway.infrastructure.standalone",
                "com.riskplatform.engine.infrastructure",
                "com.riskplatform.engine.infrastructure.standalone"
        },
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class DecisionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionGatewayApplication.class, args);
    }
}
