package com.riskplatform.gateway.agent.llm;

import com.riskplatform.gateway.agent.AgentRuntimeConfig;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import org.springframework.stereotype.Component;

/**
 * 合并网关 application.yml 默认与策略 JSON 中的 Agent/LLM 配置。
 */
@Component
public class AgentLlmConfigurer {

    private final AgentLlmProperties properties;

    public AgentLlmConfigurer(AgentLlmProperties properties) {
        this.properties = properties;
    }

    public AgentRuntimeConfig applyGatewayDefaults(AgentRuntimeConfig config) {
        if (config == null) {
            config = new AgentRuntimeConfig();
        }
        AgentLlmProperties.Orchestration orch = properties.getOrchestration();
        if (isBlank(config.llmMode)) {
            config.llmMode = orch.getDefaultLlmMode();
        }
        if (config.maxOrchestrationSteps <= 0) {
            config.maxOrchestrationSteps = orch.getDefaultMaxSteps();
        }
        if (config.llm == null) {
            config.llm = new AgentRuntimeConfig.LlmConfig();
        }
        AgentLlmProperties.Llm defaults = properties.getLlm();
        if (isBlank(config.llm.provider)) {
            config.llm.provider = defaults.getProvider();
        }
        if (isBlank(config.llm.model)) {
            config.llm.model = defaults.getDefaultModel();
        }
        if (isBlank(config.llm.apiKeyEnv)) {
            config.llm.apiKeyEnv = defaults.getApiKeyEnv();
        }
        if (isBlank(config.llm.systemPrompt)) {
            config.llm.systemPrompt = defaults.getDefaultSystemPrompt();
        }
        if (config.llm.temperature <= 0) {
            config.llm.temperature = defaults.getDefaultTemperature();
        }
        return config;
    }

    public LlmClientPort.AgentRuntimeConfigView toLlmView(AgentRuntimeConfig config) {
        AgentRuntimeConfig merged = applyGatewayDefaults(config);
        AgentRuntimeConfig.LlmConfig llm = merged.llm;
        return new LlmClientPort.AgentRuntimeConfigView(
                llm.systemPrompt,
                llm.model,
                llm.apiKeyEnv,
                properties.getLlm().getApiKey(),
                llm.temperature,
                properties.getLlm().isUseJsonResponseFormat(),
                properties.getLlm().getMaxTokens(),
                AgentLlmSupport.toKnownRiskMaps(merged.knownRisks));
    }

    public boolean isApiKeyConfigured() {
        String configured = properties.getLlm().getApiKey();
        if (configured != null && !configured.isBlank()) {
            return true;
        }
        String env = properties.getLlm().getApiKeyEnv();
        String key = System.getenv(env == null ? "DEEPSEEK_API_KEY" : env);
        return key != null && !key.isBlank();
    }

    public AgentLlmProperties properties() {
        return properties;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
