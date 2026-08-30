package com.riskplatform.gateway.adapter.agent;

import com.riskplatform.gateway.agent.llm.AgentLlmConfigurer;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运行时配置查询（不含密钥明文）。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentRuntimeController {

    private final AgentLlmConfigurer llmConfigurer;

    public AgentRuntimeController(AgentLlmConfigurer llmConfigurer) {
        this.llmConfigurer = llmConfigurer;
    }

    @GetMapping("/runtime")
    public AgentRuntimeView runtime() {
        AgentLlmProperties props = llmConfigurer.properties();
        AgentLlmProperties.Llm llm = props.getLlm();
        AgentLlmProperties.Orchestration orch = props.getOrchestration();
        return new AgentRuntimeView(
                llm.getProvider(),
                llm.getBaseUrl(),
                llm.getDefaultModel(),
                llm.getApiKeyEnv(),
                llmConfigurer.isApiKeyConfigured(),
                orch.getDefaultLlmMode(),
                orch.getDefaultMaxSteps(),
                orch.getDefaultAdoptionMode());
    }

    public record AgentRuntimeView(
            String llmProvider,
            String llmBaseUrl,
            String defaultModel,
            String apiKeyEnv,
            boolean apiKeyConfigured,
            String defaultLlmMode,
            int defaultMaxOrchestrationSteps,
            String defaultAdoptionMode) {
    }
}
