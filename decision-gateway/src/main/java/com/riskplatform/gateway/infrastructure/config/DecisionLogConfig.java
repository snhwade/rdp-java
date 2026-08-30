package com.riskplatform.gateway.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.application.DecisionExecutionLogService;
import com.riskplatform.gateway.application.InvocationTraceService;
import com.riskplatform.gateway.domain.AiAgentPort;
import com.riskplatform.gateway.domain.DecisionExecutionLogStore;
import com.riskplatform.gateway.infrastructure.client.ConfigurableAiAgent;
import com.riskplatform.gateway.infrastructure.decisionlog.AiDecisionRecordMapper;
import com.riskplatform.gateway.infrastructure.decisionlog.EngineDecisionRecordMapper;
import com.riskplatform.gateway.infrastructure.decisionlog.MySqlDecisionExecutionLogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class DecisionLogConfig {

    @Bean
    public DecisionExecutionLogStore decisionExecutionLogStore(
            EngineDecisionRecordMapper engineMapper,
            AiDecisionRecordMapper aiMapper,
            ObjectMapper objectMapper) {
        return new MySqlDecisionExecutionLogRepository(engineMapper, aiMapper, objectMapper);
    }

    @Bean
    public AiAgentPort aiAgentPort(ConfigurableAiAgent configurableAiAgent) {
        return configurableAiAgent;
    }

    @Bean(destroyMethod = "shutdown")
    public Executor aiAdviseExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public InvocationTraceService invocationTraceService(DecisionExecutionLogStore store) {
        return new InvocationTraceService(store);
    }

    @Bean
    public DecisionExecutionLogService decisionExecutionLogService(
            DecisionExecutionLogStore store,
            AiAgentPort aiAgentPort,
            Executor aiAdviseExecutor,
            com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics aiAdviseMetrics) {
        return new DecisionExecutionLogService(store, aiAgentPort, aiAdviseExecutor, aiAdviseMetrics);
    }
}
