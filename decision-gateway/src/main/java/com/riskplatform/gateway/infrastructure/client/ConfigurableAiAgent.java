package com.riskplatform.gateway.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.gateway.agent.AgentOrchestrator;
import com.riskplatform.gateway.agent.AgentRuleEvaluator;
import com.riskplatform.gateway.agent.AgentRuntimeConfig;
import com.riskplatform.gateway.agent.AgentToolContext;
import com.riskplatform.gateway.agent.AgentToolRegistry;
import com.riskplatform.gateway.agent.llm.AgentLlmConfigurer;
import com.riskplatform.gateway.agent.llm.LlmClientPort;
import com.riskplatform.gateway.domain.AiAdviseResult;
import com.riskplatform.gateway.domain.AiAgentPort;
import com.riskplatform.gateway.domain.AiLlmUnavailableException;
import com.riskplatform.gateway.domain.AgentStrategyPort;
import com.riskplatform.gateway.infrastructure.config.AgentLlmProperties;
import com.riskplatform.gateway.infrastructure.metrics.AiAdviseMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可配置 AI Agent：固定工具链 / 自主编排 / 规则 / 可选 LLM。
 */
public class ConfigurableAiAgent implements AiAgentPort {

    private static final String FALLBACK_CONFIG = """
            {
              "llmMode": "ORCHESTRATED",
              "maxOrchestrationSteps": 8,
              "featureFields": ["merchantId", "amount"],
              "knownRisks": [
                {"id": "blacklist", "name": "黑名单", "description": "主体命中精确黑名单", "signalKeys": ["blackHit"], "suggestTools": ["list_check"]},
                {"id": "watchlist", "name": "关注名单", "description": "主体在关注名单", "signalKeys": ["watchHit"], "suggestTools": ["list_check"]}
              ],
              "tools": [
                {"id": "read_context", "enabled": true},
                {"id": "list_check", "enabled": true},
                {"id": "analyze_amount_spike", "enabled": true, "spikeRatio": 3, "spikeAbsolute": 100000},
                {"id": "read_indicator", "enabled": true, "refs": [{"refName": "b2b_daily_amt", "windowDays": 1, "granularity": "DAY"}]},
                {"id": "compare_engine", "enabled": true},
                {"id": "check_known_risks", "enabled": true}
              ],
              "defaultDecision": "PASS",
              "defaultConfidence": 0.75,
              "defaultReason": "证据不足时保守放行"
            }
            """;

    private final AgentStrategyPort strategyPort;
    private final AgentToolRegistry toolRegistry;
    private final AgentRuleEvaluator ruleEvaluator;
    private final AgentOrchestrator orchestrator;
    private final LlmClientPort llmClient;
    private final ObjectMapper objectMapper;
    private final AgentLlmConfigurer llmConfigurer;
    private final AgentLlmProperties properties;
    private final AiAdviseMetrics metrics;

    public ConfigurableAiAgent(
            AgentStrategyPort strategyPort,
            AgentToolRegistry toolRegistry,
            AgentRuleEvaluator ruleEvaluator,
            AgentOrchestrator orchestrator,
            LlmClientPort llmClient,
            ObjectMapper objectMapper,
            AgentLlmConfigurer llmConfigurer,
            AgentLlmProperties properties,
            AiAdviseMetrics metrics) {
        this.strategyPort = strategyPort;
        this.toolRegistry = toolRegistry;
        this.ruleEvaluator = ruleEvaluator;
        this.orchestrator = orchestrator;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.llmConfigurer = llmConfigurer;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public AiAdviseResult advise(
            String eventId,
            String eventTypeCode,
            Map<String, Object> context,
            String engineDecision) {
        AgentRuntimeConfig config = loadConfig(eventTypeCode);
        List<Map<String, Object>> trace = new ArrayList<>();
        AgentToolContext ctx = new AgentToolContext(eventId, eventTypeCode, context, engineDecision, trace);

        if (config.isOrchestrated()) {
            return orchestrator.run(config, ctx);
        }

        toolRegistry.runConfiguredTools(config, ctx);
        AiAdviseResult heuristic = ruleEvaluator.evaluate(config, ctx);

        if (!config.isLlmEnabled()) {
            return heuristic;
        }

        boolean useLlm = !config.isHybrid()
                || "REVIEW".equalsIgnoreCase(heuristic.decision())
                || isDivergent(engineDecision, heuristic.decision());

        if (!useLlm) {
            return heuristic;
        }

        AiAdviseResult llmResult = llmClient.advise(
                llmConfigurer.toLlmView(config),
                eventTypeCode,
                engineDecision,
                trace,
                ctx.signals());

        if (llmResult != null) {
            return llmResult;
        }
        return onLlmUnavailable(config, ctx, heuristic);
    }

    private AiAdviseResult onLlmUnavailable(
            AgentRuntimeConfig config, AgentToolContext ctx, AiAdviseResult heuristic) {
        if (metrics != null) {
            metrics.llmUnavailable();
        }
        boolean allowFallback = properties == null
                || properties.getOrchestration() == null
                || properties.getOrchestration().isAllowHeuristicFallback();
        ctx.addTrace("llm_unavailable", Map.of(
                "allowHeuristicFallback", allowFallback,
                "mode", config.llmMode == null ? "" : config.llmMode));
        if (!allowFallback) {
            throw new AiLlmUnavailableException("LLM 不可用且已关闭启发式回退");
        }
        if (metrics != null) {
            metrics.heuristicFallback();
        }
        return heuristic != null ? heuristic : new AiAdviseResult(
                config.defaultDecision == null ? "PASS" : config.defaultDecision,
                config.defaultConfidence,
                "LLM 不可用，已启发式回退",
                ctx.trace());
    }

    private AgentRuntimeConfig loadConfig(String eventTypeCode) {
        AgentStrategyPort.ResolvedAgentStrategy resolved = strategyPort.resolve(eventTypeCode);
        String json = resolved != null ? resolved.configJson() : FALLBACK_CONFIG;
        AgentRuntimeConfig config = AgentRuntimeConfig.parse(objectMapper, json);
        return llmConfigurer.applyGatewayDefaults(config);
    }

    private static boolean isDivergent(String engineDecision, String agentDecision) {
        if (engineDecision == null || agentDecision == null) {
            return false;
        }
        return !engineDecision.equalsIgnoreCase(agentDecision);
    }
}
