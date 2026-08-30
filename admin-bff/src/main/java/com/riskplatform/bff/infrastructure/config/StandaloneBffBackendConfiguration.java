package com.riskplatform.bff.infrastructure.config;

import com.riskplatform.engine.infrastructure.config.DryRunConfig;
import com.riskplatform.gateway.infrastructure.config.AgentInfrastructureConfig;
import com.riskplatform.gateway.infrastructure.config.DecisionLogConfig;
import com.riskplatform.gateway.infrastructure.config.EmbeddedEngineConfiguration;
import com.riskplatform.indicator.infrastructure.config.ElasticsearchConfig;
import com.riskplatform.indicator.infrastructure.config.LogicalIndicatorConfig;
import com.riskplatform.indicator.infrastructure.config.StandaloneIndicatorBootstrapConfig;
import com.riskplatform.ruleconfig.infrastructure.config.AppServiceConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

/**
 * standalone admin-bff：嵌入各后端 REST 适配器，进程内 MockMvc 调度 {@code /api/v1/**}。
 */
@Configuration
@ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "standalone", matchIfMissing = true)
@Import({
        AppServiceConfig.class,
        com.riskplatform.screening.infrastructure.ScreeningConfig.class,
        BffStandaloneRatingConfig.class,
        BffStandaloneOrderQueryConfig.class,
        BffStandaloneDecisionQueryConfig.class,
        BffStandaloneRuleConfigAuthConfig.class,
        DecisionLogConfig.class,
        AgentInfrastructureConfig.class,
        EmbeddedEngineConfiguration.class,
        DryRunConfig.class,
        LogicalIndicatorConfig.class,
        StandaloneIndicatorBootstrapConfig.class,
        ElasticsearchConfig.class,
        com.riskplatform.indicator.infrastructure.config.AppServiceConfig.class
})
@ComponentScan(basePackages = {
        "com.riskplatform.ruleconfig.adapter",
        "com.riskplatform.ruleconfig.application",
        "com.riskplatform.ruleconfig.infrastructure",
        "com.riskplatform.screening.adapter",
        "com.riskplatform.screening.application",
        "com.riskplatform.screening.infrastructure",
        "com.riskplatform.rating.adapter",
        "com.riskplatform.rating.application",
        "com.riskplatform.rating.infrastructure",
        "com.riskplatform.gateway.adapter.order",
        "com.riskplatform.gateway.adapter.agent",
        "com.riskplatform.gateway.agent",
        "com.riskplatform.gateway.infrastructure.standalone",
        "com.riskplatform.engine.adapter",
        "com.riskplatform.engine.infrastructure.configcache",
        "com.riskplatform.indicator.adapter.indicator",
        "com.riskplatform.indicator.infrastructure.standalone",
        "com.riskplatform.indicator.infrastructure.stats",
        "com.riskplatform.indicator.infrastructure.redis",
        "com.riskplatform.indicator.infrastructure.es"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.SecurityConfig"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppServiceConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.riskplatform.indicator.infrastructure.config.AppServiceConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = ElasticsearchConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = LogicalIndicatorConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = StandaloneIndicatorBootstrapConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.riskplatform.rating.infrastructure.RatingConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.riskplatform.gateway.infrastructure.standalone.EmbeddedEngineGateway.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.riskplatform.gateway.adapter.event.RiskEventController.class)
})
@MapperScan(
        basePackages = {
                "com.riskplatform.ruleconfig.infrastructure",
                "com.riskplatform.screening.infrastructure",
                "com.riskplatform.rating.infrastructure",
                "com.riskplatform.gateway.infrastructure.order",
                "com.riskplatform.gateway.infrastructure.decisionlog",
                "com.riskplatform.gateway.infrastructure.standalone",
                "com.riskplatform.engine.infrastructure",
                "com.riskplatform.indicator.infrastructure.standalone"
        },
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class StandaloneBffBackendConfiguration {
}
