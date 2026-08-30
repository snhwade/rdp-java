package com.riskplatform.engine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 规则引擎+决策引擎服务启动入口。
 *
 * <p>限界上下文：风控判定。负责选择器匹配、规则执行（Aviator）、决策聚合、
 * 决策日志与执行链路记录、决策表执行（S2）。
 */
@SpringBootApplication
@MapperScan({
        "com.riskplatform.engine.infrastructure",
        "com.riskplatform.engine.infrastructure.standalone"
})
public class RuleDecisionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleDecisionEngineApplication.class, args);
    }
}
