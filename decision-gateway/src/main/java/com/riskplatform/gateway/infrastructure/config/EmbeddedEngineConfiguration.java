package com.riskplatform.gateway.infrastructure.config;

import com.riskplatform.engine.infrastructure.config.DecisionOrchestrationConfig;
import com.riskplatform.engine.infrastructure.config.DecisionToolsConfig;
import com.riskplatform.engine.infrastructure.config.DryRunConfig;
import com.riskplatform.engine.infrastructure.config.ObservabilityConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * standalone 模式：在网关进程内装配 rule-decision-engine 核心 bean（不暴露 8083 端口）。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
@Import({DecisionOrchestrationConfig.class, DecisionToolsConfig.class, DryRunConfig.class, ObservabilityConfig.class})
@ComponentScan(basePackages = {
        "com.riskplatform.engine.application",
        "com.riskplatform.engine.infrastructure.rulepackage",
        "com.riskplatform.engine.infrastructure.decisionflow",
        "com.riskplatform.engine.infrastructure.decisiontool",
        "com.riskplatform.engine.infrastructure.decisionlog",
        "com.riskplatform.engine.infrastructure.strategyoutput",
        "com.riskplatform.engine.infrastructure.runtime",
        "com.riskplatform.engine.infrastructure.dryrun",
        "com.riskplatform.engine.infrastructure.standalone",
        "com.riskplatform.engine.infrastructure.configcache"
})
public class EmbeddedEngineConfiguration {
}
