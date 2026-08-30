package com.riskplatform.ruleconfig;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 规则配置服务启动入口。
 *
 * <p>限界上下文：规则与配置管理。负责事件类型、规则、规则组、选择器、指标定义、
 * 决策优先级配置的 CRUD 与校验。
 */
@SpringBootApplication
@MapperScan("com.riskplatform.ruleconfig.infrastructure")
public class RuleConfigServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleConfigServiceApplication.class, args);
    }
}
