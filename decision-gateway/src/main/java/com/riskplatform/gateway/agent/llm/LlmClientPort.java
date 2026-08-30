package com.riskplatform.gateway.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 可选 LLM 推理（OpenAI 兼容 API）。
 */
public interface LlmClientPort {

    /**
     * 一次性推理（LLM / HYBRID 模式）。
     */
    com.riskplatform.gateway.domain.AiAdviseResult advise(
            AgentRuntimeConfigView config,
            String eventTypeCode,
            String engineDecision,
            List<Map<String, Object>> trace,
            Map<String, Object> signals);

    /**
     * 自主编排单步：调用工具或结束并给出决策。
     *
     * @return null 表示 LLM 不可用，编排应终止并降级。
     */
    OrchestrationStep orchestrateStep(
            AgentRuntimeConfigView config,
            String eventTypeCode,
            String engineDecision,
            List<Map<String, Object>> toolCatalog,
            List<Map<String, Object>> trace,
            Map<String, Object> signals,
            int stepIndex);

    record AgentRuntimeConfigView(
            String systemPrompt,
            String model,
            String apiKeyEnv,
            String apiKey,
            double temperature,
            boolean useJsonResponseFormat,
            int maxTokens,
            List<Map<String, Object>> knownRisks) {
    }

    record OrchestrationStep(
            String action,
            String tool,
            Map<String, Object> args,
            String decision,
            double confidence,
            String reason,
            List<Map<String, Object>> knownHits,
            List<Map<String, Object>> unknownFindings) {

        public boolean isFinish() {
            return "finish".equalsIgnoreCase(action);
        }
    }
}
