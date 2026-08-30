package com.riskplatform.gateway.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.agent.AgentOrchestrator;
import com.riskplatform.gateway.agent.AgentRuleEvaluator;
import com.riskplatform.gateway.agent.AgentToolRegistry;
import com.riskplatform.gateway.agent.llm.LlmClientPort;
import com.riskplatform.gateway.agent.llm.OpenAiLlmClient;
import com.riskplatform.gateway.domain.AgentStrategyPort;
import com.riskplatform.gateway.domain.IndicatorReadGateway;
import com.riskplatform.gateway.domain.ListGateway;
import com.riskplatform.gateway.domain.ScreeningGateway;
import com.riskplatform.gateway.infrastructure.client.ConfigurableAiAgent;
import com.riskplatform.gateway.infrastructure.client.RestAgentStrategyLoader;
import com.riskplatform.gateway.infrastructure.client.RestIndicatorReadGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AgentInfrastructureConfig {

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public AgentStrategyPort remoteAgentStrategyPort(
            @Value("${downstream.rule-config:http://localhost:8082}") String ruleConfigBaseUrl) {
        return new RestAgentStrategyLoader(RestClient.create(), ruleConfigBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "rdp.integration.mode", havingValue = "remote")
    public IndicatorReadGateway remoteIndicatorReadGateway(
            @Value("${downstream.indicator-store:http://localhost:8084}") String indicatorBaseUrl) {
        return new RestIndicatorReadGateway(RestClient.create(), indicatorBaseUrl);
    }

    @Bean
    public AgentToolRegistry agentToolRegistry(
            ListGateway listGateway,
            ScreeningGateway screeningGateway,
            IndicatorReadGateway indicatorReadGateway) {
        return new AgentToolRegistry(listGateway, screeningGateway, indicatorReadGateway);
    }

    @Bean
    public AgentRuleEvaluator agentRuleEvaluator() {
        return new AgentRuleEvaluator();
    }

    @Bean
    public LlmClientPort llmClientPort(ObjectMapper objectMapper, AgentLlmProperties agentLlmProperties) {
        return new OpenAiLlmClient(
                RestClient.create(),
                objectMapper,
                agentLlmProperties.getLlm().getBaseUrl());
    }

    @Bean
    public com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics aiAdviseMetrics(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics(meterRegistry);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(
            AgentToolRegistry agentToolRegistry,
            LlmClientPort llmClientPort,
            com.riskplatform.gateway.agent.llm.AgentLlmConfigurer agentLlmConfigurer,
            AgentLlmProperties agentLlmProperties,
            com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics aiAdviseMetrics) {
        return new AgentOrchestrator(
                agentToolRegistry, llmClientPort, agentLlmConfigurer, agentLlmProperties, aiAdviseMetrics);
    }

    @Bean
    public ConfigurableAiAgent configurableAiAgent(
            AgentStrategyPort agentStrategyPort,
            AgentToolRegistry agentToolRegistry,
            AgentRuleEvaluator agentRuleEvaluator,
            AgentOrchestrator agentOrchestrator,
            LlmClientPort llmClientPort,
            ObjectMapper objectMapper,
            com.riskplatform.gateway.agent.llm.AgentLlmConfigurer agentLlmConfigurer,
            AgentLlmProperties agentLlmProperties,
            com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics aiAdviseMetrics) {
        return new ConfigurableAiAgent(
                agentStrategyPort,
                agentToolRegistry,
                agentRuleEvaluator,
                agentOrchestrator,
                llmClientPort,
                objectMapper,
                agentLlmConfigurer,
                agentLlmProperties,
                aiAdviseMetrics);
    }
}
